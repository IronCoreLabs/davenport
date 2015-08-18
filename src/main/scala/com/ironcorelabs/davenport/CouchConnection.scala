//
// com.ironcorelabs.davenport.CouchConnection
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import scala.language.implicitConversions
import DB._

// Couchbase
import com.couchbase.client.core._
import com.couchbase.client.java.{ ReplicateTo, PersistTo, ReplicaMode, CouchbaseCluster, Bucket, AsyncBucket }
import com.couchbase.client.java.env.{ CouchbaseEnvironment, DefaultCouchbaseEnvironment }
import com.couchbase.client.java.document._
import com.couchbase.client.java.error._
import java.util.NoSuchElementException

// RxScala (Observables) used in Couchbase client lib async calls
import rx.lang.scala._
import rx.lang.scala.JavaConversions._
import rx.lang.scala.Notification._

// Configuration library
import knobs.{ Required, Optional, FileResource, Config, ClassPathResource }
import java.io.File

object CouchConnection extends AbstractConnection {
  /*
   * Building block types for couchbase connection
   */
  private case class CouchConnectionConfig(host: String, bucketName: String, env: CouchbaseEnvironment)
  private case class CouchConnectionInfo(cluster: CouchbaseCluster, bucket: Bucket, env: CouchbaseEnvironment)

  /*
   * Stateful connection details
   */
  private var currentConnection: Option[CouchConnectionInfo] = None
  private var testConnection: Option[CouchConnectionInfo] = None
  private def bucketOrError: Throwable \/ Bucket = currentConnection.map(_.bucket) \/> new Exception("Not connected")

  /*
   * Configuration
   */
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
        .queryEnabled(cfg.lookup[Boolean]("cdb.queryEnabled") getOrElse false)
        .ioPoolSize(cfg.lookup[Int]("cdb.ioPoolSize") getOrElse 4)
        .computationPoolSize(cfg.lookup[Int]("cdb.computationPoolSize") getOrElse 4)
        .kvEndpoints(cfg.lookup[Int]("cdb.kvEndpoints") getOrElse 2)
        .build()
    )
  }

  /*
   * Connect and disconnect state methods
   */
  def connect: Throwable \/ Unit = dbconfig.map { cfg =>
    try {
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

  def disconnect(): Unit = {
    currentConnection.map { c =>
      c.cluster.disconnect
      c.env.shutdown
    }
    currentConnection = None
    ()
  }

  def connected: Boolean = !currentConnection.isEmpty

  /*
   * Free grammar implementation to run a DBProg using couchbase backend
   */
  def apply[A](db: DBProg[A]): Throwable \/ A = exec(db)
  def exec[A](db: DBProg[A]): Throwable \/ A = execTask(db).attemptRun.join
  def execTask[A](db: DBProg[A]): Task[Throwable \/ A] = if (connected) {
    Free.runFC[DBOp, Task, Throwable \/ A](db.run)(couchRunner)
  } else {
    // should I just connect, do it, then disconnect in this case?
    // probably better to just error
    Task.fail(new Exception("Not connected"))
  }

  /*
   * We use co-yoneda to run our Free.
   * In this case, the couchRunner object transforms DBOp to Task
   * The only public method, apply, is what gets called as the grammar
   * is executed, calling it to transform DBOps to functions.
   */
  def couchRunner = new (DBOp ~> Task) {
    def apply[A](dbp: DBOp[A]): Task[A] = dbp match {
      case GetDoc(k: Key) => getDoc(k)
      case CreateDoc(k: Key, v: RawJsonString) => createDoc(k, v)
      case GetCounter(k: Key) => getCounter(k)
      case IncrementCounter(k: Key, delta: Long) => incrementCounter(k, delta)
      case RemoveKey(k: Key) => removeKey(k)
      case UpdateDoc(k: Key, v: RawJsonString, h: HashVerString) => updateDoc(k, v, h)
      case BatchCreateDocs(st: DBBatchStream, continue: (Throwable => Boolean)) =>
        batchCreateDocs(st, continue)
    }

    /*
     * Helpers for the grammar interpreter
     */
    private def getDoc(k: Key): Task[Throwable \/ DbValue] =
      couchOp2DbV(_.get(k.value, classOf[RawJsonDocument]))

    private def createDoc(k: Key, v: RawJsonString): Task[Throwable \/ DbValue] =
      couchOp2DbV(_.insert(
        RawJsonDocument.create(k.value, 0, v.value, 0)
      ))

    private def getCounter(k: Key): Task[Throwable \/ Long] =
      couchOp2Long(_.counter(k.value, 0, 0, 0))

    private def incrementCounter(k: Key, delta: Long): Task[Throwable \/ Long] =
      couchOp2Long(
        // here we use delta as the default, so if you want an increment
        // by one on a key that doesn't exist, we'll give you a 1 back
        // and if you want an increment by 10 on a key that doesn't exist,
        // we'll give you a 10 back
        _.counter(k.value, delta, delta, 0)
      )

    private def removeKey(k: Key): Task[Throwable \/ Unit] =
      couchOp2DbV(
        _.remove(k.value, classOf[RawJsonDocument])
      ).map(_ => ().right)

    private def updateDoc(k: Key, v: RawJsonString, h: HashVerString): Task[Throwable \/ DbValue] =
      couchOp2DbV(_.replace(
        RawJsonDocument.create(k.value, 0, v.value, h.value.toLong)
      ))

    private def batchCreateDocs(st: DBBatchStream, continue: (Throwable => Boolean)): Task[Throwable \/ DBBatchResults] =
      evalBatchStream(batchStream2StreamOfResults(st), continue).map(_.right)

    /*
     * Helpers for batchCreateDocs
     */
    def batchStream2StreamOfResults(st: DBBatchStream): Iterator[DBBatchResults] =
      st.zipWithIndex.map {
        (altogether: (Throwable \/ (DBProg[Key], RawJsonString), Int)) =>
          val (rec, idx) = altogether
          // We have nested disjunctions. Will unwind these to
          // the DBBatchResult error summary format
          disj2BatchResult(rec, idx, { progKeyAndV: (DBProg[Key], RawJsonString) =>
            val (edbpk, v) = progKeyAndV
            disj2BatchResult(exec(edbpk), idx, { k: Key =>
              createDoc(k, v).attemptRun
                .toThese.bimap(e => IList((idx, e)), _ => IList(idx))
            })
          })
      }

    def disj2BatchResult[A](res: Throwable \/ A, idx: Int, f: A => DBBatchResults): DBBatchResults =
      res.fold(e => batchFailed(idx, e), a => f(a))

    def stopIteratingWhenContinueFunctionFails(st: Iterator[DBBatchResults], continue: Throwable => Boolean): Iterator[DBBatchResults] = {
      var lastLineAndError = none[DBBatchResults]
      st.takeWhile {
        case \&/.This(ilist: IList[(Int, Throwable)]) => ilist.headOption.fold(true) {
          case (idx, e) => continue(e) || {
            lastLineAndError = batchFailed(idx, e).some
            false
          }
          case _ => true
        }
        case _ => true
        // hack to return the last error when the continue function
        // aborts further processing (takeWhile won't return it)
      } ++ lastLineAndError.toIterator
    }

    def evalBatchStream(st: Iterator[DBBatchResults], continue: (Throwable => Boolean)): Task[DBBatchResults] = {
      val emptyResult: DBBatchResults = IList[Int]().wrapThat[IList[(Int, Throwable)]]
      Task.delay(
        stopIteratingWhenContinueFunctionFails(st, continue)
          .reduceOption(_ |+| _)
          .getOrElse(emptyResult)
      )
    }

    // Convenience for taking an observable, handling errors and wrapping
    // the results up into a DbValue (json string + version hash) result
    // wrapped in a task with explicit errors
    private def couchOp2DbV(f: AsyncBucket => Observable[RawJsonDocument]): Task[Throwable \/ DbValue] = {
      val eOrT: Throwable \/ Task[RawJsonDocument] = for {
        b <- bucketOrError
        ba = b.async
      } yield obs2Task(f(ba))
      // Take my Throwable \/ Task[RawJsonDocument] (eOrT) and convert to
      // Task[Throwable \/ DbValue]
      eOrT.fold(
        Task.fail(_),
        _.map { doc =>
          DbValue(RawJsonString(doc.content), HashVerString(doc.cas.toString)).right
        }
      )
    }

    // Convenience for taking an observable, handling errors and wrapping
    // the results up into a Long wrapped in a task with explicit errors
    private def couchOp2Long(f: AsyncBucket => Observable[JsonLongDocument]): Task[Throwable \/ Long] = {
      val eOrT: Throwable \/ Task[JsonLongDocument] = for {
        b <- bucketOrError
        ba = b.async
      } yield obs2Task(f(ba))
      eOrT.fold(
        Task.fail(_),
        _.map(doc => \/.fromTryCatchNonFatal(doc.content.toLong))
      )
    }

    // This is the most efficient way of running things in the couchbase lib
    // -- far more efficient then using the blocking observables. This converts
    // the callbacks from the observable into a task, which is easier to work
    // with when composing and reasoning about functions and operations.
    private def obs2Task[A](o: Observable[A]): Task[A] = {
      Task.async[A](k => {
        // o.take(1).single.subscribe(
        o.firstOrElse(throw new DocumentDoesNotExistException()).subscribe(
          n => k(n.right),
          e => k(e.left),
          () => ()
        )
        ()
      })
    }
  }

  /*
   * Next two functions provided for unit tests
   */
  def fakeDisconnect() = {
    testConnection = currentConnection
    currentConnection = None
  }
  def fakeDisconnectRevert() = {
    currentConnection = testConnection
  }

}

/*
 *
 * Modeling signatures in Couchbase -- a tour of the options.
 *
 * 1. Embed signatures into PgpKey.  So the signer PgpKey would have a list of
 *    outbound signatures and would get a List[PgpSignature].  Inbound signatures
 *    would require a view.
 *
 * 2. Put signatures into their own keyspace like
 *    "PgpSignature::signer-signee-created" although there is no way to scan
 *    or find this list without views or some other index back.  But could have
 *    references to the sigs from PgpKeys.
 *
 * 3. Put signatures in their own documents in a key space under the signer key
 *    so they can be predictably fetched.  In this scenario, we would have a
 *    document like: "Key::signerkeyid::signatures_count" that contained the
 *    number of outbound signatures and we'd have an incrementing counter:
 *    "Key::signerkeyid::signatures::incrid"
 *
 *    In this way we could paginate through and predict keys reliably.
 *
 *    Could also do something similar for inbound signatures, but just
 *    referencing the source key.
 */

