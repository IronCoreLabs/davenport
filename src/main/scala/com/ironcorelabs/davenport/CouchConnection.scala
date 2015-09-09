//
// com.ironcorelabs.davenport.CouchConnection
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import DB._
import scalaz.stream.Process

// Couchbase
import com.couchbase.client.java.{ ReplicateTo, PersistTo, ReplicaMode, CouchbaseCluster, Bucket, AsyncBucket }
import com.couchbase.client.java.env.{ CouchbaseEnvironment, DefaultCouchbaseEnvironment }
import com.couchbase.client.java.document._
import com.couchbase.client.java.error._
import java.util.NoSuchElementException

// RxScala (Observables) used in Couchbase client lib async calls
import rx.lang.scala._
import rx.lang.scala.JavaConversions._

// Configuration library
import knobs.{ Required, Optional, FileResource, Config, ClassPathResource }
import java.io.File

/** Connect to Couchbase and interpret [[DB.DBProg]]s */
object CouchConnection extends AbstractConnection {
  //
  //
  // Building block types for couchbase connection
  //
  //

  private case class CouchConnectionConfig(host: String, bucketName: String, env: CouchbaseEnvironment)
  private case class CouchConnectionInfo(cluster: CouchbaseCluster, bucket: Bucket, env: CouchbaseEnvironment)

  //
  //
  // Stateful connection details
  //
  //
  private var currentConnection: Option[CouchConnectionInfo] = None
  private var testConnection: Option[CouchConnectionInfo] = None
  private def bucketOrError: Throwable \/ Bucket = currentConnection.map(_.bucket) \/> new Exception("Not connected")

  //
  //
  // Configuration
  //
  //

  private val configFileName = "couchbase.cfg"
  private val configFileDevName = "couchbase-dev.cfg"
  private val config: Task[Config] = knobs.loadImmutable(
    Optional(ClassPathResource(configFileName))
      :: Optional(FileResource(new File(configFileDevName)))
      :: Nil
  )
  private val dbconfig: Task[CouchConnectionConfig] = config.map { cfg =>
    CouchConnectionConfig(
      cfg.lookup[String]("cdb.host") getOrElse "couchbase.local",
      cfg.lookup[String]("cdb.bucketName") getOrElse "default",
      DefaultCouchbaseEnvironment.builder()
        // .queryEnabled(cfg.lookup[Boolean]("cdb.queryEnabled") getOrElse false)
        .ioPoolSize(cfg.lookup[Int]("cdb.ioPoolSize") getOrElse 4)
        .computationPoolSize(cfg.lookup[Int]("cdb.computationPoolSize") getOrElse 4)
        .kvEndpoints(cfg.lookup[Int]("cdb.kvEndpoints") getOrElse 2)
        .build()
    )
  }

  //Natural transformation from DbOps to Task allowing the replacement of DBOps
  //as the Monad in a Process.
  private val dbOpsToTask: DBOps ~> Task = new (DBOps ~> Task) {
    def apply[A](db: DBOps[A]): Task[A] = {
      Free.runFC[DBOp, Task, A](db)(couchRunner)
    }
  }
  //
  //
  // Connect and disconnect state methods
  //
  //

  /**
   * Connect to couchbase using the on-disk configuration
   *
   *  Configuration details should be specified in `couchbase.cfg`
   *  located in the classpath, or `couchbase-dev.cfg` located
   *  in the root of the project.
   *
   *  This is done on a global (static) object as the underlying
   *  couchbase libraries require at most one connection and then
   *  pool requests to that endpoint.
   */
  def connect: Throwable \/ Unit = connectWithConfig(dbconfig)
  def connectToHost(host: String): Throwable \/ Unit = connectWithConfig(dbconfig.map { cfg =>
    cfg.copy(host = host)
  })
  def connectWithConfig(dbcfg: Task[CouchConnectionConfig]): Throwable \/ Unit = dbcfg.map { cfg =>
    try {
      println("Attempting connection to " + cfg.host)
      val cluster = CouchbaseCluster.create(cfg.env, cfg.host)
      currentConnection = Some(CouchConnectionInfo(
        cluster,
        cluster.openBucket(cfg.bucketName),
        cfg.env
      ))
      ().right
    } catch {
      case e: Exception => {
        currentConnection = None
        e.left
      }
    }
  }.attemptRun.join

  /** Safely disconnect from couchbase */
  def disconnect(): Unit = {
    currentConnection.map { c =>
      c.cluster.disconnect
      c.env.shutdown
    }
    currentConnection = None
    ()
  }

  /**
   * Check if a connection is currently open
   *
   *  Note: this is no guarantee that the connection remains
   *  open. This indicates a previous successful connection
   *  and no disconnect. Should the server go down after
   *  connect, for example, this will return `true` though
   *  attempts to use the connection will fail.
   */
  def connected: Boolean = !currentConnection.isEmpty

  /**
   * Free grammar implementation to run a `DBProg` using couchbase backend
   */
  def exec[A](db: DBProg[A]): Throwable \/ A = execTask(db).attemptRun.join

  def translateProcess[A](db: Process[DBOps, A]): Process[Task, A] = {
    db.translate(dbOpsToTask)
  }

  /**
   * an alias to exec
   */
  def apply[A](db: DBProg[A]): Throwable \/ A = exec(db)

  /**
   * Wrap the execution of the [[DB.DBProg]] in a `scalaz.concurrent.Task`
   */
  def execTask[A](db: DBProg[A]): Task[Throwable \/ A] = if (connected) {
    Free.runFC[DBOp, Task, Throwable \/ A](db.run)(couchRunner)
  } else {
    // should I just connect, do it, then disconnect in this case?
    // probably better to just error
    Task.fail(new Exception("Not connected"))
  }

  /**
   * We use co-yoneda to run our `scalaz.Free`.
   *
   * In this case, the couchRunner object transforms [[DB.DBOp]] to
   * `scalaz.concurrent.Task`.
   * The only public method, apply, is what gets called as the grammar
   * is executed, calling it to transform [[DB.DBOps]] to functions.
   */
  private val couchRunner = new (DBOp ~> Task) {
    def apply[A](dbp: DBOp[A]): Task[A] = dbp match {
      case GetDoc(k: Key) => getDoc(k)
      case CreateDoc(k: Key, v: RawJsonString) => createDoc(k, v)
      case GetCounter(k: Key) => getCounter(k)
      case IncrementCounter(k: Key, delta: Long) => incrementCounter(k, delta)
      case RemoveKey(k: Key) => removeKey(k)
      case UpdateDoc(k: Key, v: RawJsonString, h: HashVer) => updateDoc(k, v, h)
    }

    /*
     * Helpers for the grammar interpreter
     */
    private def getDoc(k: Key): Task[Throwable \/ DbValue] =
      couchOpToDBValue(_.get(k.value, classOf[RawJsonDocument]))

    private def createDoc(k: Key, v: RawJsonString): Task[Throwable \/ DbValue] =
      couchOpToDBValue(_.insert(
        RawJsonDocument.create(k.value, 0, v.value, 0)
      ))

    private def getCounter(k: Key): Task[Throwable \/ Long] =
      couchOpToLong(_.counter(k.value, 0, 0, 0))

    private def incrementCounter(k: Key, delta: Long): Task[Throwable \/ Long] =
      couchOpToLong(
        // here we use delta as the default, so if you want an increment
        // by one on a key that doesn't exist, we'll give you a 1 back
        // and if you want an increment by 10 on a key that doesn't exist,
        // we'll give you a 10 back
        _.counter(k.value, delta, delta, 0)
      )

    private def removeKey(k: Key): Task[Throwable \/ Unit] =
      couchOpToA[Unit, String](
        _.remove(k.value, classOf[RawJsonDocument])
      )(_ => ().right)

    private def updateDoc(k: Key, v: RawJsonString, h: HashVer): Task[Throwable \/ DbValue] =
      couchOpToA(_.replace(
        RawJsonDocument.create(k.value, 0, v.value, h.value)
      ))(doc => DbValue(RawJsonString(doc.content), HashVer(doc.cas)).right)

    private def couchOpToLong(fetchOp: AsyncBucket => Observable[JsonLongDocument]): Task[Throwable \/ Long] = {
      couchOpToA(fetchOp)(doc => \/.fromTryCatchNonFatal(doc.content))
    }

    private def couchOpToDBValue(fetchOp: AsyncBucket => Observable[RawJsonDocument]): Task[Throwable \/ DbValue] =
      couchOpToA(fetchOp)(doc => DbValue(RawJsonString(doc.content), HashVer(doc.cas)).right)

    private def couchOpToA[A, B](fetchOp: AsyncBucket => Observable[AbstractDocument[B]])(f: AbstractDocument[B] => Throwable \/ A): Task[Throwable \/ A] = {
      val eOrT: Throwable \/ Task[Throwable \/ AbstractDocument[B]] = for {
        b <- bucketOrError
        ba = b.async
      } yield obs2Task(fetchOp(ba))
      // Take my Throwable \/ Task[RawJsonDocument] (eOrT) and convert to
      // Task[Throwable \/ DbValue]
      eOrT.fold(
        Task.fail(_),
        _.map { docOrError =>
          docOrError.flatMap(f)
        }
      )
    }

    // This is the most efficient way of running things in the couchbase lib
    // -- far more efficient then using the blocking observables. This converts
    // the callbacks from the observable into a task, which is easier to work
    // with when composing and reasoning about functions and operations.
    private def obs2Task[A](o: Observable[A]): Task[Throwable \/ A] = {
      Task.async[A](k => {
        o.headOption.subscribe(
          n => k(n.map(_.right).getOrElse(new DocumentDoesNotExistException().left)),
          e => k(e.left),
          () => ()
        )
        ()
      }).attempt
    }
  }

  /**
   * Used for testing a failed connection without having
   * to disconnect from the database first.
   */
  def fakeDisconnect() = {
    testConnection = currentConnection
    currentConnection = None
  }
  /**
   * Restores the connected session state
   */
  def fakeDisconnectRevert() = {
    currentConnection = testConnection
  }

}
