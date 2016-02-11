//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scala.collection.concurrent.TrieMap

import scalaz.concurrent.Task

// Couchbase
import com.couchbase.client.core.CouchbaseCore
import com.couchbase.client.core.env.{ DefaultCoreEnvironment, CoreEnvironment }

import internal.Bucket
import datastore.{ CouchDatastore, Datastore }
import rx.lang.scala.JavaConversions._

/**
 * Create a connection to the Couchbase Cluster, which will allow you to create a [[com.ironcorelabs.davenport.datastore.CouchDatastore]]
 * for a particular bucket.
 * Only one of CouchConnection should be created at a time in an application as it handles all the underlying threading and is expensive
 * to create.
 *
 * This is primarily used to for the purpose of calling [[CouchConnection.openDatastore]] which creates a
 * [[com.ironcorelabs.davenport.datastore.Datastore]] for the [[BucketNameAndPassword]]
 * that was passed in. The datastore created can be reused or discarded.
 */
final case class CouchConnection(config: DavenportConfig) {
  private val environment = createConfig
  private val maybeCore = internal.CouchbaseCoreUtil.createCouchbaseCore(environment, config.hosts).attemptRun
  //This is only package private for testing. Use openBucket if you need a bucket.
  private[davenport] val openBuckets: TrieMap[BucketNameAndPassword, Bucket] = new TrieMap()

  private def createConfig = DefaultCoreEnvironment.builder()
    .ioPoolSize(config.ioPoolSize)
    .computationPoolSize(config.computationPoolSize)
    .kvEndpoints(config.kvEndpoints)
    .build()

  /**
   * Open a bucket for the bucketAndPassword and create a Datastore from it.
   */
  def openDatastore(bucketAndPassword: BucketNameAndPassword): Datastore = new CouchDatastore(openBucket(bucketAndPassword))

  /**
   * Attempt to get the current cached bucket for the BucketNameAndPassword. If it isn't in the cache, create a new one.
   */
  def openBucket(bucketAndPassword: BucketNameAndPassword): Task[Bucket] = Task.delay {
    openBuckets.get(bucketAndPassword).map(Task.now(_)).getOrElse {
      createNewBucket(bucketAndPassword.name, bucketAndPassword.password).map { newBucket =>
        openBuckets.putIfAbsent(bucketAndPassword, newBucket).getOrElse(newBucket)
      }
      //We have to run here because the rx.exceptions.OnErrorFailedException escapes the task, the outer Task.delay catches.
    }.run
  }.handleWith {
    //This exception happens when the cluster and environment have been shut down. Try and give a slightly
    //better message.
    case ex: java.util.concurrent.RejectedExecutionException =>
      Task.fail(new DisconnectedException(ex))
    case ex: rx.exceptions.OnErrorFailedException =>
      Task.fail(new DisconnectedException(ex))
  }

  /**
   * Returns a Task[Boolean] where the boolean represents if the bucket was actually closed or not.
   */
  def closeBucket(bucketAndName: BucketNameAndPassword): Task[Boolean] = Task.delay {
    openBuckets.remove(bucketAndName).isDefined
  }

  /**
   * Disconnect this CouchConnection. The Connection will no longer be usable and all datastores created from it will
   * no longer work.
   */
  def disconnect: Task[Boolean] = for {
    core <- Task.fromDisjunction(maybeCore)
    _ <- internal.CouchbaseCoreUtil.disconnectCouchbaseCore(core)
    environmentResultAsJava <- util.observable.toSingleItemTask(environment.shutdownAsync)
    environmentResult = Boolean2boolean(environmentResultAsJava)
    _ <- Task.delay(openBuckets.clear)
  } yield environmentResult

  private def createNewBucket(bucketName: String, password: Option[String]): Task[Bucket] = for {
    core <- Task.fromDisjunction(maybeCore)
    bucket <- internal.CouchbaseCoreUtil.openBucket(core, bucketName, password)
  } yield bucket
}

final case class BucketNameAndPassword(name: String, password: Option[String])

/**
 * Indicates that the Exception is due to the underlying connection being terminated.
 */
final class DisconnectedException(inner: Exception)
  extends Exception(" Cluster appears to have been disconnected and cannot be used.", inner)
