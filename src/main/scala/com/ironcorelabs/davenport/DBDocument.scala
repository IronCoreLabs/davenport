//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz.\/

// Couchbase
import com.couchbase.client.java.{ ReplicateTo, PersistTo, ReplicaMode }

// Picklers
import argonaut._, Argonaut._

import DB._

/**
 * Should be implemented by a wrapper of a case class
 *
 *  Typically you'll have this pattern:
 *
 *  {{{
 *  case class User(name: String, email: String)
 *  class DBUser(val key: Key, data: User, hv: HashVer) extends DBDocument[User] {
 *    // ...
 *  }
 *  object DBUser extends DBDocumentCompanion[User] {
 *    // ...
 *  }
 *  }}}
 *
 *  If you follow this pattern, you'll be able to store/retrieve arbitrary
 *  documents of different types.
 */
trait DBDocument[T] {
  /** Typically put in the parameters for the `DBDocument` class. */
  val key: Key

  /** Typically put in the parameters for the `DBDocument` class. */
  val data: T

  /**
   * Typically put in the parameters for the `DBDocument` class.
   *
   *  Used to capture the database state when this was fetched.
   */
  val cas: Long

  /** Generate a json string from the data */
  def dataJson: Throwable \/ RawJsonString

  /** Remove this from the database */
  def remove: DBProg[Unit] = removeKey(key)

  /** Convenience function used on update */
  lazy val hashver = HashVer(cas)
}

/**
 * For the companion object for the [[DBDocument]]
 *
 *  See [[DBDocument]] docs for more details.
 */
trait DBDocumentCompanion[T] {
  /** This assumes argonaut. */
  implicit def codec: CodecJson[T]

  /**
   * Keys may be generated from database ops like IncrementCounter
   *
   *  Consequently, `genKey` returns a [[DB.DBProg]]. To return a static string,
   *  just lift the value into a `DBProg`, like this:
   *
   * {{{
   *  def genKey = liftIntoDBProg(Key("mykey").right)
   * }}}
   */
  def genKey(d: T): DBProg[_]

  /**
   * Convert from a json string to a `T`
   *
   *  Return the left disjunction on error.
   *
   *  If you have `object DBUser extends DBDocumentCompanion[User]`
   *  then this method should return a `right` of `User` on success.
   */
  def fromJson(s: RawJsonString): Throwable \/ T

  /**
   * Take a document of type `T` and add to the datastore
   *
   *  This should return a `DBWhatever` when the [[DB.DBProg]] is executed.
   */
  def create(d: T): DBProg[_]

  /** Fetch a document from the datastore */
  def get(k: Key): DBProg[_]

  /** Remove an arbitrary document from the datastore */
  def remove(k: Key): DBProg[Unit] = removeKey(k)

  /** Helper method for implementing interface */
  def fromJsonString(s: String)(implicit codec: CodecJson[T]): Option[T] = s.decodeOption[T]

  /** Helper method for implementing interface */
  def toJsonString(t: T)(implicit codec: CodecJson[T]): RawJsonString = RawJsonString(t.asJson.toString)
}
