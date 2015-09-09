//
// com.ironcorelabs.davenport.DB
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import scalaz.stream.Process

/**
 * Contains the primitives for building DBProg programs for later execution by
 *  a connection.
 *
 * To use this, import com.ironcorelabs.davenport.DB._
 */
object DB {

  //
  //
  // 1. Basic building block types encoded as scala value classes (google that)
  //
  //

  /** Just a string. This is used for type safety. */
  final case class Key(value: String) extends AnyVal

  /** Just a string. This is used for type safety. */
  final case class RawJsonString(value: String) extends AnyVal

  /**
   * A hashed signature of an existing value in the db.
   *
   *  Couchbase calls this a CAS (check and save) as it is passed back
   *  in with requests to update a value. If the value has been changed
   *  by another actor, then the update fails and the caller is left
   *  to handle the conflict.
   */
  final case class HashVer(value: Long) extends AnyVal

  /** Just a string and a hashver */
  final case class DbValue(jsonString: RawJsonString, hashVer: HashVer)

  //
  //
  // 2. Type aliases for simpler function signatures
  //
  //

  /** A Free Co-yoneda of [[DBOp]] classes */
  type DBOps[A] = Free.FreeC[DBOp, A]

  /**
   * The basic building block sent to the interpreter.
   *
   *  Effectively this captures expected return type of `Throwable \/ A`
   *  and a series of [[DBOp]] classes or functions combined together.
   *  When passed to an interpreter such as `CouchConnection.exec` or
   *  `MemConnection.exec`, these are executed.
   */
  type DBProg[A] = EitherT[DBOps, Throwable, A]

  //
  //
  // 3. Lifts required for dealing with Coyoneda and wrapper types
  //
  //

  implicit val MonadDBOps: Monad[DBOps] = Free.freeMonad[Coyoneda[DBOp, ?]]

  /**
   * The `liftIntoDBProg` operations allow any function or value to be deferred to
   *  the executor.
   *
   *  This will most often be used when using for comprehensions mixing [[DBOp]]
   *  operations with other data extraction such as json de/serialization.
   */
  def liftIntoDBProg[A](opt: Option[A]): DBProg[A] = liftIntoDBProg(opt, "Value not found")

  /**
   * The `liftIntoDBProg` operations allow any function or value to be deferred to
   *  the executor.
   *
   *  This will most often be used when using for comprehensions mixing [[DBOp]]
   *  operations with other data extraction such as json de/serialization.
   */
  def liftIntoDBProg[A](either: Throwable \/ A): DBProg[A] = EitherT.eitherT(Monad[DBOps].point(either))

  /**
   * The liftIntoDBProg operations allow any function or value to be deferred to
   *  the executor.
   *
   *  This will most often be used when using `for` comprehensions mixing [[DBOp]]
   *  operations with other data extraction such as json de/serialization.
   */
  def liftIntoDBProg[A](opt: Option[A], errormessage: String): DBProg[A] = EitherT.eitherT(Monad[DBOps].point(opt \/> new Exception(errormessage)))

  private def liftToFreeEitherT[A](a: DBOp[Throwable \/ A]): DBProg[A] = {
    val free: DBOps[Throwable \/ A] = Free.liftFC(a)
    EitherT.eitherT(free)
  }

  //
  //
  // 4. Algebraic Data Type of DB operations. (A persistence grammar of sorts.)
  //
  //

  /** Any database operation must be represented by a `DBOp` */
  sealed trait DBOp[+A]
  case class GetDoc(key: Key) extends DBOp[Throwable \/ DbValue]
  case class CreateDoc(key: Key, doc: RawJsonString) extends DBOp[Throwable \/ DbValue]
  case class UpdateDoc(key: Key, doc: RawJsonString, hashver: HashVer) extends DBOp[Throwable \/ DbValue]
  case class RemoveKey(key: Key) extends DBOp[Throwable \/ Unit]
  case class GetCounter(key: Key) extends DBOp[Throwable \/ Long]
  case class IncrementCounter(key: Key, delta: Long = 1) extends DBOp[Throwable \/ Long]

  //
  //
  // 5. Building block functions for building DBProgs on basic db ops
  //
  //

  /** Return a document given some key */
  def getDoc(k: Key): DBProg[DbValue] = liftToFreeEitherT(GetDoc(k))

  /** Create a document with the given key */
  def createDoc(k: Key, doc: RawJsonString): DBProg[DbValue] =
    liftToFreeEitherT(CreateDoc(k, doc))

  /** Update a doc given its key, new value, and correct hashver */
  def updateDoc(k: Key, doc: RawJsonString, hashver: HashVer): DBProg[DbValue] =
    liftToFreeEitherT(UpdateDoc(k, doc, hashver))

  /** Remove a doc from the DB given its key */
  def removeKey(k: Key): DBProg[Unit] = liftToFreeEitherT(RemoveKey(k))

  /** Fetch a counter from the DB given a key */
  def getCounter(k: Key): DBProg[Long] = liftToFreeEitherT(GetCounter(k))

  /** Increment a counter in the DB and return for some key and delta */
  def incrementCounter(k: Key, delta: Long = 1): DBProg[Long] =
    liftToFreeEitherT(IncrementCounter(k, delta))

  /**
   * Convenience function to fetch a doc and transform it via a function `f`
   *
   *  In practice, this is more an example showing how to build a function like
   *  this. More commonly a higher level class that can be serialized to the db
   *  will have a modify function that transforms and calls this under the hood.
   */
  def modifyDoc(k: Key, f: RawJsonString => RawJsonString): DBProg[DbValue] = for {
    t <- getDoc(k)
    res <- updateDoc(k, f(t.jsonString), t.hashVer)
  } yield res

  /**
   * Object that contains batch operations for DB. They have been separated out because they cannot be mixed with `DBProg` operations
   * without first lifting them into a process via `liftToProcess`.
   */
  object Batch {
    def liftToProcess[A](prog: DBProg[A]): Process[DBOps, Throwable \/ A] = Process.eval(prog.run)
    /**
     * Create all values in the Foldable F.
     */
    def createDocs[F[_]](foldable: F[(Key, RawJsonString)])(implicit F: Foldable[F]): Process[DBOps, Throwable \/ DbValue] = {
      Process.emitAll(foldable.toList).evalMap { case (key, json) => createDoc(key, json).run }
    }
  }
}
