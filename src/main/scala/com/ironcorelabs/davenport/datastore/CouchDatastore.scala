//
// com.ironcorelabs.davenport.CouchDatastore
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import scalaz.{ \/, \/-, ~>, Kleisli, Free }
import scalaz.concurrent.Task
import scalaz.syntax.either._
import DB._
import scalaz.stream.Process

// Couchbase
import com.couchbase.client.java.{ ReplicateTo, PersistTo, ReplicaMode, CouchbaseCluster, Bucket, AsyncBucket }
import com.couchbase.client.java.env.{ CouchbaseEnvironment, DefaultCouchbaseEnvironment }
import com.couchbase.client.java.document.{ AbstractDocument, JsonLongDocument, RawJsonDocument }
import com.couchbase.client.java.error._

// RxScala (Observables) used in Couchbase client lib async calls
import rx.lang.scala.Observable
import rx.lang.scala.JavaConversions._

final case class CouchDatastore(bucket: Task[Bucket]) extends Datastore {
  import CouchDatastore._
  /**
   * Given a Bucket return back a NT that can turn DBOps into a Task.
   */
  def execute: (DBOps ~> Task) = new (DBOps ~> Task) {
    def apply[A](prog: DBOps[A]): Task[A] =
      bucket.flatMap(executeK(prog).run(_))
  }
}

/**
 * Things related to translating DBOp to Kleisli[Task, Bucket, A] and some helpers for translating to Task[A].
 *
 * This object contains couchbase specific things such as CAS, which is modeled as CommitVersion.
 *
 * For details about how this translation is done, look at couchRunner which routes each DBOp to its couchbase
 * counterpart.
 */
final object CouchDatastore {
  type CouchK[A] = Kleisli[Task, Bucket, A]

  /**
   * Interpret the program into a Kleisli that will take a Bucket as its argument. Useful if you want to do
   * Kleisli arrow composition before running it.
   */
  def executeK[A](prog: DBProg[A]): CouchK[DBError \/ A] = executeK(prog.run)

  /**
   * Basic building block. Turns the DbOps into a Kleisli which takes a Bucket, used by execute above.
   */
  def executeK[A](prog: DBOps[A]): CouchK[A] = Free.runFC[DBOp, CouchK, A](prog)(couchRunner)

  /**
   * In this case, the couchRunner object transforms [[DB.DBOp]] to
   * `scalaz.concurrent.Task`.
   * The only public method, apply, is what gets called as the grammar
   * is executed, calling it to transform [[DB.DBOps]] to functions.
   */
  private val couchRunner = new (DBOp ~> CouchK) {
    def apply[A](dbp: DBOp[A]): Kleisli[Task, Bucket, A] = dbp match {
      case GetDoc(k: Key) => getDoc(k)
      case CreateDoc(k: Key, v: RawJsonString) => createDoc(k, v)
      case GetCounter(k: Key) => getCounter(k)
      case IncrementCounter(k: Key, delta: Long) => incrementCounter(k, delta)
      case RemoveKey(k: Key) => removeKey(k)
      case UpdateDoc(k: Key, v: RawJsonString, cv: CommitVersion) => updateDoc(k, v, cv)
    }

    /*
     * Helpers for the datastore
     */
    private def getDoc(k: Key): CouchK[DBError \/ DBValue] =
      couchOpToDBValue(k)(_.get(k.value, classOf[RawJsonDocument]))

    private def createDoc(k: Key, v: RawJsonString): CouchK[DBError \/ DBValue] =
      couchOpToDBValue(k)(_.insert(
        RawJsonDocument.create(k.value, 0, v.value, 0)
      ))

    private def getCounter(k: Key): CouchK[DBError \/ Long] =
      couchOpToLong(k)(_.counter(k.value, 0, 0, 0))

    private def incrementCounter(k: Key, delta: Long): CouchK[DBError \/ Long] =
      couchOpToLong(k)(
        // here we use delta as the default, so if you want an increment
        // by one on a key that doesn't exist, we'll give you a 1 back
        // and if you want an increment by 10 on a key that doesn't exist,
        // we'll give you a 10 back
        _.counter(k.value, delta, delta, 0)
      )

    private def removeKey(k: Key): CouchK[DBError \/ Unit] =
      couchOpToA[Unit, String](
        _.remove(k.value, classOf[RawJsonDocument])
      )(_ => ()).map(_.leftMap(throwableToDBError(k, _)))

    private def updateDoc(k: Key, v: RawJsonString, cv: CommitVersion): CouchK[DBError \/ DBValue] = {
      val updateResult = couchOpToA(_.replace(
        RawJsonDocument.create(k.value, 0, v.value, cv.value)
      ))(doc => DBDocument(k, CommitVersion(doc.cas), RawJsonString(doc.content)))

      updateResult.map(_.leftMap(throwableToDBError(k, _)))
    }

    private def couchOpToLong(k: Key)(fetchOp: AsyncBucket => Observable[JsonLongDocument]): CouchK[DBError \/ Long] = {
      couchOpToA(fetchOp)(doc => Long2long(doc.content)).map(_.leftMap(throwableToDBError(k, _)))
    }

    // These lines are too long for scalastyle, but can't find a way to stop
    // scalastyle from pasting them back together wherever I split them, so
    // they are getting the big ignore hammer.
    private def couchOpToDBValue(k: Key)(fetchOp: AsyncBucket => Observable[RawJsonDocument]): CouchK[DBError \/ DBValue] = // scalastyle:ignore
      couchOpToA(fetchOp)(doc => DBDocument(k, CommitVersion(doc.cas), RawJsonString(doc.content))).
        map(_.leftMap(throwableToDBError(k, _)))

    private def couchOpToA[A, B](fetchOp: AsyncBucket => Observable[AbstractDocument[B]])(f: AbstractDocument[B] => A): Kleisli[Task, Bucket, Throwable \/ A] = { // scalastyle:ignore

      Kleisli.kleisli { bucket: Bucket =>
        obs2Task(fetchOp(bucket.async)).map(_.map(f))
      }
    }

    private def throwableToDBError(key: Key, t: Throwable): DBError = t match {
      case _: DocumentDoesNotExistException => ValueNotFound(key)
      case _: DocumentAlreadyExistsException => ValueExists(key)
      case _: CASMismatchException => CommitVersionMismatch(key)
      case t => GeneralError(t)
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
}
