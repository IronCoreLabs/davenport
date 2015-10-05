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
    def dbget[T](implicit codec: DecodeJson[T]): DBProg[DBDocument[T]] = DBDocument.get(key)(codec)
    def dbremove = DBDocument.remove(key)
    def dbcreate[T](t: T)(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] = DBDocument.create(key, t)
    def dbincrementCounter(delta: Long): DBProg[Long] = DB.incrementCounter(key, delta)
    def dbgetCounter: DBProg[Long] = DB.getCounter(key)
  }
}
