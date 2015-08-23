//
// com.ironcorelabs.davenport.DBDocument
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import scala.language.implicitConversions

// Couchbase
import com.couchbase.client.core._
import com.couchbase.client.java.{ ReplicateTo, PersistTo, ReplicaMode }
import com.couchbase.client.java.document._

// RxScala (Observables)
import rx.lang.scala._
import rx.lang.scala.JavaConversions._
import rx.lang.scala.Notification._

// Picklers
import argonaut._, Argonaut._

import DB._

trait DBDocument[T] {
  val key: Key
  val data: T
  val cas: Long
  def dataJson: Throwable \/ RawJsonString
  // def modify(f: T => T): DBProg[_]
  def remove: DBProg[Unit] = removeKey(key)
  lazy val hashver = HashVer(cas)
}

trait DBDocumentCompanion[T] {
  implicit def codec: CodecJson[T]

  // Interface contract
  def genKey(d: T): DBProg[_]
  def fromJson(s: RawJsonString): Throwable \/ T
  def create(d: T): DBProg[_]
  def get(k: Key): DBProg[_]
  // TODO: add some kind of validation on the key namespace?
  def remove(k: Key): DBProg[Unit] = removeKey(k)

  // Helper methods for implementing interface
  def fromJsonString(s: String)(implicit codec: CodecJson[T]): Option[T] = s.decodeOption[T]
  def toJsonString(t: T)(implicit codec: CodecJson[T]): RawJsonString = RawJsonString(t.asJson.toString)
}
