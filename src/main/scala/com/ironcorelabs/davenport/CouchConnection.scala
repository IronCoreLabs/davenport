//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scala.collection.concurrent.TrieMap

import scalaz.concurrent.Task
import scalaz.syntax.monad._ // Brought in for .join

// Couchbase
import com.couchbase.client.java.{ CouchbaseCluster, Bucket, AsyncBucket, ConnectionString }
import com.couchbase.client.java.env.{ CouchbaseEnvironment, DefaultCouchbaseEnvironment }

import datastore.{ CouchDatastore, Datastore }
import rx.lang.scala.JavaConversions._
import scala.collection.JavaConverters._

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
  private val (environment, cluster) = createCouchbaseCluster(config)
  //This is only package private for testing. Use openBucket if you need a bucket.
  private[davenport] val openBuckets: TrieMap[BucketNameAndPassword, Bucket] = new TrieMap()

  private final def createCouchbaseCluster(config: DavenportConfig): (CouchbaseEnvironment, CouchbaseCluster) = {
    val environment = DefaultCouchbaseEnvironment.builder()
      .ioPoolSize(config.ioPoolSize)
      .computationPoolSize(config.computationPoolSize)
      .kvEndpoints(config.kvEndpoints)
      .build()
    val cluster = CouchbaseCluster.create(environment, config.hosts.list.asJava)
    environment -> cluster
  }

  /**
   * Open a bucket for the bucketAndPassword and create a Datastore from it.
   */
  def openDatastore(bucketAndPassword: BucketNameAndPassword): Datastore = new CouchDatastore(openBucket(bucketAndPassword))

  /**
   * Attempt to get the current cached bucket for the BucketNameAndPassword. If it isn't in the cache, create a new one.
   */
  def openBucket(bucketAndPassword: BucketNameAndPassword): Task[Bucket] = Task.delay {
    openBuckets.get(bucketAndPassword).map(Task.now(_)).getOrElse {
      createNewBucket(bucketAndPassword.name, bucketAndPassword.password).flatMap { newBucket =>
        closeNewAndKeepOld(openBuckets.putIfAbsent(bucketAndPassword, newBucket), newBucket)
      }
    }.handleWith {
      //This exception happens when the cluster and environment have been shut down. Try and give a slightly
      //better message.
      case ex: java.util.concurrent.RejectedExecutionException =>
        Task.fail(new DisconnectedException(ex))
    }
  }.join

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
    clusterResult <- Task.delay(cluster.disconnect)
    environmentResultAsJava <- util.observable.toSingleItemTask(environment.shutdown)
    environmentResult = environmentResultAsJava.map(x => Boolean2boolean(x)).getOrElse(false)
    _ <- Task.delay(openBuckets.clear)
  } yield environmentResult && clusterResult

  //In the case of maybeOldBucket being defined, we don't want to keep new. It's possibly true that others already have a handle
  //on old so prefer it and close the new.
  private[davenport] def closeNewAndKeepOld(maybeOldBucket: Option[Bucket], newBucket: Bucket) = Task.delay {
    maybeOldBucket match {
      case None => newBucket
      case Some(oldBucket) =>
        newBucket.close //close the new one
        oldBucket
    }
  }

  private def createNewBucket(bucketName: String, password: Option[String]): Task[Bucket] = Task.delay {
    password.map(cluster.openBucket(bucketName, _)).getOrElse(cluster.openBucket(bucketName))
  }
}

final case class BucketNameAndPassword(name: String, password: Option[String])

/**
 * Indicates that the Exception is due to the underlying connection being terminated.
 */
final class DisconnectedException(inner: Exception)
  extends Exception(" Cluster appears to have been disconnected and cannot be used.", inner)
