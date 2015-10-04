//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import DB.{ Key, DBProg }
import argonaut.EncodeJson

final object dbdocument extends DBDocumentOps

trait DBDocumentOps {
  //This might not belong here. I don't know where else to put it though? 
  //maybe syntax.id?
  implicit class TOps[T](t: T) {
    def create(key: Key)(implicit codec: EncodeJson[T]): DBProg[DBDocument[T]] =
      DBDocument.create(key, t)
  }
}
