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
import com.couchbase.client.java.{ CouchbaseCluster, Bucket, AsyncBucket }
import com.couchbase.client.java.env.{ CouchbaseEnvironment, DefaultCouchbaseEnvironment }

// Configuration library
import knobs.{ Required, Optional, FileResource, Config, ClassPathResource }
import java.io.File

/** Connect to Couchbase and interpret [[DB.DBProg]]s */
object CouchConnection {
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
  private val bucketOrError: Task[Bucket] = Task.delay(currentConnection.map(_.bucket).getOrElse(throw new Exception("Not connected")))

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

  def apply[A](prog: DBProg[A]): Throwable \/ A = exec(prog)

  def execProcess[A](p: Process[DBOps, A]): Process[Task, A] = {
    Process.eval(bucketOrError).flatMap { bucket =>
      p.translate(CouchTranslator.interpret(bucket))
    }
  }

  def exec[A](prog: DBProg[A]): Throwable \/ A = execTask(prog).attemptRun.join

  def execTask[A](prog: DBProg[A]): Task[Throwable \/ A] = {
    bucketOrError.flatMap { bucket =>
      val progToTask = CouchTranslator.interpret(bucket)
      progToTask(prog.run)
    }
  }

}
