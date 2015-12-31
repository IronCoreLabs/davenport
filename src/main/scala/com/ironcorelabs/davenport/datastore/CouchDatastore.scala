//
// com.ironcorelabs.davenport.CouchDatastore
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import scalaz.{ \/, Kleisli, ~>, Free }
import scalaz.concurrent.Task
import db._

import internal.Bucket

/**
 * Create a CouchDatastore which operates on the bucket provided. Note that the primary way this should be used is through
 * [[CouchConnection.openDatastore]].
 */
final case class CouchDatastore(bucket: Task[Bucket]) extends Datastore {
  import CouchDatastore._
  /**
   * A function from DBOps[A] => Task[A] which operates on the bucket provided.
   */
  def execute: (DBOps ~> Task) = new (DBOps ~> Task) {
    def apply[A](prog: DBOps[A]): Task[A] = bucket.flatMap(executeK(prog).run(_))
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
    def apply[A](dbp: DBOp[A]): CouchK[A] = dbp match {
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
    private def getDoc(k: Key): CouchK[DBError \/ DBValue] = bucketToA(k)(_.get[RawJsonString](k))
    private def createDoc(k: Key, v: RawJsonString): CouchK[DBError \/ DBValue] = bucketToA(k)(_.create(k, v))
    private def removeKey(k: Key): CouchK[DBError \/ Unit] = bucketToA(k)(_.remove(k).map(_ => ()))
    private def getCounter(k: Key): CouchK[DBError \/ Long] = bucketToA(k)(_.getCounter(k).map(_.data))
    private def incrementCounter(k: Key, delta: Long): CouchK[DBError \/ Long] =
      bucketToA(k)(_.incrementCounter(k, delta).map(_.data))
    private def updateDoc(k: Key, v: RawJsonString, cv: CommitVersion): CouchK[DBError \/ DBValue] =
      bucketToA(k) { _.update(k, v, cv.value) }

    private def bucketToA[A, B](key: Key)(fetchOp: Bucket => Task[A]): Kleisli[Task, Bucket, DBError \/ A] =
      Kleisli.kleisli { bucket: Bucket => attemptAndMap(key, fetchOp(bucket)) }
    private def attemptAndMap[A](key: Key, t: Task[A]): Task[DBError \/ A] = t.attempt.map(_.leftMap(throwableToDBError(key, _)))

    private def throwableToDBError(key: Key, t: Throwable): DBError = t match {
      case _: error.DocumentDoesNotExistException => ValueNotFound(key)
      case _: error.DocumentAlreadyExistsException => ValueExists(key)
      case _: error.CASMismatchException => CommitVersionMismatch(key)
      case t => GeneralError(t)
    }
  }
}
