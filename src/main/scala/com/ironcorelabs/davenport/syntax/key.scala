//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import db.{ Key, DBProg, DBDocument }
import argonaut.{ DecodeJson, EncodeJson, CodecJson }

// The convention is for syntax objects to start with lower case, so they look
// like package names. Scalastyle doesn't care for this, so ignore the line.
final object key extends KeyOps // scalastyle:ignore

trait KeyOps {
  implicit class OurKeyOps(key: Key) {
    def dbGet[T](implicit codec: DecodeJson[T]): DBProg[DBDocument[T]] = DBDocument.get(key)(codec)
    def dbRemove: DBProg[Unit] = DBDocument.remove(key)
    def dbCreate[T](t: T)(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] = DBDocument.create(key, t)
    def dbIncrementCounter(delta: Long): DBProg[Long] = db.incrementCounter(key, delta)
    def dbGetCounter: DBProg[Long] = db.getCounter(key)
    def dbModify[T](f: T => T)(implicit codec: CodecJson[T]): DBProg[DBDocument[T]] = DBDocument.modify(key, f)
  }
}
