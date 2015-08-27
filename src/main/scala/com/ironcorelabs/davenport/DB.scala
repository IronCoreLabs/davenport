//
// com.ironcorelabs.davenport.DB
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import scala.language.implicitConversions

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

  /**
   * A batch error gives a record number and an error string
   *
   *  When importing a lot of data, this will store accrued errors
   *  indicating their source.
   */
  final case class DbBatchError(recordNum: Int, error: Throwable)

  /**
   * Makes use of `scalaz.These` (`\&/`) to accumulate successes and failures
   *
   *  `\&/.This` will capture a list of encountered errors and their line nums
   *
   *  `\&/.That` will capture a list of successfully imported line nums
   */
  type DBBatchResults = (IList[DbBatchError] \&/ IList[Int])

  /**
   * Transform incoming data into this type
   *
   *  If a failure happens, for example, reading data from a file,
   *  pass that failure along in the form of an Exception. Otherwise
   *  pass in a key and a json string.
   *
   *  Note: the key must be encapsulated in [[DBProg]]. This allows you
   *  to increment a counter in the DB for the next key. But if your
   *  key is statically derived from the data, use the appropriate lift
   *  function, like this:
   *
   *  {{{
   *  liftIntoDBProg(Key("mykey").right)
   *  }}}
   *
   *  If you have an issue generating a key or it fails validation of
   *  some kind, lift your error into [[DBProg]] instead.
   */
  type DBBatchStream = Iterator[Throwable \/ (DBProg[Key], RawJsonString)]

  //
  //
  // 3. Lifts required for dealing with Coyoneda and wrapper types
  //
  //

  implicit val MonadDBOps: Monad[DBOps] = Free.freeMonad[({ type l[a] = Coyoneda[DBOp, a] })#l]

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

  /** Convenience for converting an exception into a [[DBProg]] operation */
  def dbProgFail[A](e: Throwable): DBProg[A] = liftIntoDBProg(e.left)

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
  case class BatchCreateDocs(st: DBBatchStream, continue: Throwable => Boolean) extends DBOp[Throwable \/ DBBatchResults]

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
   * Process a stream of records into new database documents
   *
   *  Pass in a continue function to control what happens on error. For example,
   *  if you want to abort imports on certain errors or after a certain number
   *  of errors, use a custom `continue` function. By default, the processing of
   *  the incoming records does not stop until the end of the records are
   *  reached.
   */
  def batchCreateDocs(st: DBBatchStream, continue: Throwable => Boolean = _ => true): DBProg[DBBatchResults] =
    liftToFreeEitherT(BatchCreateDocs(st, continue))

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

  //
  // Other type conveniences
  //

  /** Generate a [[DBBatchResults]] error */
  def batchFailed(idx: Int, e: Throwable): DBBatchResults =
    IList(DbBatchError(idx, e)).wrapThis[IList[Int]]

  /** Generate a [[DBBatchResults]] success */
  def batchSucceeded(idx: Int): DBBatchResults =
    IList(idx).wrapThat[IList[DbBatchError]]
}
