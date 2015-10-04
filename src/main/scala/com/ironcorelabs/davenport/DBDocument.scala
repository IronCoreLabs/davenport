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
  def genKey(d: T): DBProg[Key]

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
  def get(k: Key)(implicit codec: DecodeJson[T]): DBProg[Option[T]] = for {
    s <- getDoc(k)
  } yield fromJsonString(s.jsonString.value)

  /** Remove an arbitrary document from the datastore */
  def remove(k: Key): DBProg[Unit] = removeKey(k)

  /** Helper method for implementing interface */
  def fromJsonString(s: String)(implicit codec: DecodeJson[T]): Option[T] = s.decodeOption[T]

  /** Helper method for implementing interface */
  def toJsonString(t: T)(implicit codec: EncodeJson[T]): RawJsonString = RawJsonString(t.asJson.toString)
}

case class Document[A](key: Key, hashVer: HashVer, data: A) {
  def map[B](f: A => B) = Document(key, hashVer, f(data))
}

object Document {
  import scalaz.Functor
  implicit val instances: Functor[Document] = new Functor[Document] {
    def map[A, B](fa: Document[A])(f: A => B): Document[B] = fa.map(f)
  }

  def create[T](key: Key, t: T)(implicit codec: EncodeJson[T]): DBProg[Document[RawJsonString]] =
    createDoc(key, RawJsonString(t.asJson.toString)).map(dbv => Document(key, dbv.hashVer, dbv.jsonString))

  /** Fetch a document from the datastore */
  def get[T](k: Key)(implicit codec: DecodeJson[T]): DBProg[Document[Option[T]]] = for {
    s <- getDoc(k)
  } yield Document(k, s.hashVer, s.jsonString.value.decodeOption[T])

  /** Remove an arbitrary document from the datastore */
  def remove(k: Key): DBProg[Unit] = removeKey(k)
}

object documentSyntax {
  implicit class KeyOps(key: Key) {
    def get[T](implicit codec: DecodeJson[T]) = Document.get(key)(codec)
    def remove = Document.remove(key)
  }
  implicit class TOps[T](t: T) {
    def create(key: Key)(implicit codec: EncodeJson[T]): DBProg[Document[RawJsonString]] =
      Document.create(key, t)
  }
}
