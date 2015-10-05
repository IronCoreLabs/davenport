//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import DB.{ Key, DBProg }
import argonaut.{ DecodeJson, EncodeJson }

final object key extends KeyOps

trait KeyOps {
  implicit class OurKeyOps(key: Key) {
    def dbGet[T](implicit codec: DecodeJson[T]): DBProg[DBDocument[T]] = DBDocument.get(key)(codec)
    def dbRemove = DBDocument.remove(key)
    def dbCreate[T](t: T)(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] = DBDocument.create(key, t)
    def dbIncrementCounter(delta: Long): DBProg[Long] = DB.incrementCounter(key, delta)
    def dbGetCounter: DBProg[Long] = DB.getCounter(key)
  }
}
