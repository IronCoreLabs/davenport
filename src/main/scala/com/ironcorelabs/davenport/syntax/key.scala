//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import DB.{ Key, DBProg }
import argonaut.DecodeJson

final object key extends KeyOps

trait KeyOps {
  implicit class OurKeyOps(key: Key) {
    def get[T](implicit codec: DecodeJson[T]) = DBDocument.get(key)(codec)
    def remove = DBDocument.remove(key)
  }
}
