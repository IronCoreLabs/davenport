//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import DB._
import interpreter.MemInterpreter

class DBSpec extends TestBase {
  "DB" should {
    "fail lifting none into dbprog" in {
      val interpreter = MemInterpreter(Map())
      interpreter.interpret(liftIntoDBProg(None)).run should be(left)
    }
  }
}
