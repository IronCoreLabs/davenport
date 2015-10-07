//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import argonaut._, Argonaut._
import DB._

/**
 * A document that's either destined to be put into the DB or came out of the DB.
 * Key - The key where the document is stored.
 * hashVer - The HashVer of the current document
 * data - The data stored in the document, typically RawJsonString when it comes out of the DB.
 */
final case class DBDocument[A](key: Key, hashVer: HashVer, data: A) {
  def map[B](f: A => B) = DBDocument(key, hashVer, f(data))
}

final object DBDocument {
  import scalaz.{ Functor, Equal }
  import scalaz.std.string._
  import scalaz.std.anyVal._
  implicit val instance: Functor[DBDocument] = new Functor[DBDocument] {
    def map[A, B](fa: DBDocument[A])(f: A => B): DBDocument[B] = fa.map(f)
  }

  implicit def dbDocumentEqual[A](implicit aEq: Equal[A]): Equal[DBDocument[A]] = new Equal[DBDocument[A]] {
    override def equalIsNatural: Boolean = aEq.equalIsNatural

    override def equal(doc1: DBDocument[A], doc2: DBDocument[A]): Boolean = (doc1, doc2) match {
      case (DBDocument(key1, hashVer1, a1), DBDocument(key2, hashVer2, a2)) =>
        aEq.equal(a1, a2) && Equal[String].equal(key1.value, key2.value) && Equal[Long].equal(hashVer1.value, hashVer2.value)
    }
  }

  /**
   * Create a document out of `t` using `codec` and store it at `key`.
   */
  def create[T](key: Key, t: T)(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] =
    createDoc(key, RawJsonString(t.asJson.toString)).map(_.map(_ => t))

  /**
   * Fetch a document from the datastore and decode it using `codec`
   * If deserialization fails the DBProg will result in a left disjunction.
   */
  def get[T](k: Key)(implicit codec: DecodeJson[T]): DBProg[DBDocument[T]] = for {
    s <- getDoc(k)
    v <- liftIntoDBProg(s.data.value.decodeOption[T], "Deserialization failed.")
  } yield DBDocument(k, s.hashVer, v)

  /**
   * A short way to get and update by running the data through `f`.
   */
  def modify[T](k: Key, f: T => T)(implicit codec: CodecJson[T]): DBProg[DBDocument[T]] = for {
    v <- get(k)(codec)
    result <- update(v.map(f))
  } yield result

  /**
   * Update the document to a new value.
   */
  def update[T](doc: DBDocument[T])(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] =
    updateDoc(doc.key, RawJsonString(doc.data.asJson.toString), doc.hashVer).map(newDoc => newDoc.map(_ => doc.data))

  /**
   * Remove the document stored at key `key`
   */
  def remove(key: Key): DBProg[Unit] = removeKey(key)
}
