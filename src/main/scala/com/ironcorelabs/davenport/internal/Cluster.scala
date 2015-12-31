package com.ironcorelabs.davenport
package internal

import util.observable.toSingleItemTask
import com.couchbase.client.core.config.ConfigurationException
import com.couchbase.client.core.message.cluster.{ OpenBucketRequest, OpenBucketResponse, SeedNodesRequest, SeedNodesResponse }
import com.couchbase.client.core.{ CouchbaseCore, CouchbaseException }
import error.{ BucketDoesNotExistException, InvalidPasswordException }
import com.couchbase.client.core.env.{ DefaultCoreEnvironment, CoreEnvironment }
import com.couchbase.client.core.message.cluster.{ DisconnectResponse, DisconnectRequest }

import rx.lang.scala.JavaConversions._
import scalaz.concurrent.Task
import scalaz.NonEmptyList
import scala.collection.JavaConverters._

/**
 * A grouping of functions that apply to working with the [[CouchbaseCore]]. The object is used only to denote namespaceing
 */
private[davenport] final object CouchbaseCoreUtil {

  final def disconnectCouchbaseCore(core: CouchbaseCore): Task[Unit] =
    toSingleItemTask(core.send[DisconnectResponse](new DisconnectRequest())).map(_ => ())
  /**
   * Create the [[CouchbaseCore]] for the environment. These shouldn't be created by anyone but the [[CouchConnection]]
   */
  final def createCouchbaseCore(environment: CoreEnvironment, hosts: NonEmptyList[String]): Task[CouchbaseCore] = for {
    core <- Task.delay(new CouchbaseCore(environment))
    initialized <- initializeSeedNodes(core, hosts)
    result <- if (initialized) Task.now(core) else Task.fail(new Exception("Failed to initialize."))
  } yield result

  /**
   * Initialize core by sending it the seed nodes.
   */
  final def initializeSeedNodes(core: CouchbaseCore, nodes: NonEmptyList[String]): Task[Boolean] =
    toSingleItemTask(core.send[SeedNodesResponse](new SeedNodesRequest(nodes.list.asJava))).map(_.status.isSuccess)

  /**
   * Ask core to open a bucket with bucketName as its name and possibly a password.
   */
  final def openBucket(core: CouchbaseCore, bucketName: String, maybePassword: Option[String]): Task[Bucket] = {
    val password = maybePassword.getOrElse("")
    toSingleItemTask(core.send[OpenBucketResponse](new OpenBucketRequest(bucketName, password))).flatMap { response =>
      if (response.status.isSuccess) {
        Task.now(Bucket(core, bucketName))
      } else {
        Task.fail(new CouchbaseException(s"Couldn't open $bucketName."))
      }
    }.handleWith {
      case ex: ConfigurationException =>
        ex.getCause match {
          case _: IllegalStateException if ex.getCause.getMessage.contains("NOT_EXISTS") =>
            Task.fail(new BucketDoesNotExistException(bucketName))
          case _: IllegalStateException if ex.getCause.getMessage.contains("Unauthorized") =>
            Task.fail(new InvalidPasswordException(bucketName))
          case _ =>
            Task.fail(ex)
        }
      case ex: CouchbaseException =>
        Task.fail(ex)
    }
  }
}
