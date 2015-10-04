//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import DB._
import interpreter.MemInterpreter
import syntax._

class DBSpec extends TestBase {
  "DB" should {
    "fail lifting none into dbprog" in {
      val interpreter = MemInterpreter.empty
      val error = liftIntoDBProg(None, ValueNotFound(Key("blah"))).interpret(interpreter).run.leftValue
      error.message should include("blah")
    }

    "fail with a GeneralError when lifting none into dbprog with message" in {
      val interpreter = MemInterpreter.empty
      val error = interpreter.interpret(liftIntoDBProg(None, "blah")).run.leftValue
      error shouldBe an[GeneralError]
      error.message should include("blah")
    }

    "fail with a GeneralError when lifting a throwable into a DBProg" in {
      val interpreter = MemInterpreter.empty
      val ex = new Exception("I am an exception hear me roar!")
      val error = interpreter.interpret(liftIntoDBProg(ex.left)).run.leftValue
      error shouldBe an[GeneralError]
      error.message shouldBe ex.getMessage
    }

    "fail with a GeneralError when lifting a DBError into a DBProg" in {
      val interpreter = MemInterpreter.empty
      val error = liftDisjunction(ValueNotFound(Key("blah")).left).interpret(interpreter).run.leftValue
      error.message should include("blah")
    }
  }
}
