//
// com.ironcorelabs.davenport.DBSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import DB._

class DBSpec extends TestBase {
  "DB" should {
    "fail lifting none into dbprog" in {
      MemInterpreter.interpretTask(liftIntoDBProg(None)).run._2 should be(left)
    }
  }
}
