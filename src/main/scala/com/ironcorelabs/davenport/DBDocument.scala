//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import argonaut._, Argonaut._
import DB._

final case class DBDocument[A](key: Key, hashVer: HashVer, data: A) {
  def map[B](f: A => B) = DBDocument(key, hashVer, f(data))
}

final object DBDocument {
  import scalaz.Functor
  implicit val instances: Functor[DBDocument] = new Functor[DBDocument] {
    def map[A, B](fa: DBDocument[A])(f: A => B): DBDocument[B] = fa.map(f)
  }

  def create[T](key: Key, t: T)(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] =
    createDoc(key, RawJsonString(t.asJson.toString)).map(_.map(_ => t))

  /** Fetch a document from the datastore */
  def get[T](k: Key)(implicit codec: DecodeJson[T]): DBProg[DBDocument[Option[T]]] = for {
    s <- getDoc(k)
  } yield DBDocument(k, s.hashVer, s.data.value.decodeOption[T])

  /** Remove an arbitrary document from the datastore */
  def remove(k: Key): DBProg[Unit] = removeKey(k)
}
