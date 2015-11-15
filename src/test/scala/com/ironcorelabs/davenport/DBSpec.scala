//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import DB._
import datastore.MemDatastore
import syntax._

class DBSpec extends TestBase {
  "DB" should {
    "fail lifting none into dbprog" in {
      val datastore = MemDatastore.empty
      val error = liftIntoDBProg(None, ValueNotFound(Key("blah"))).execute(datastore).run.leftValue
      error.message should include("blah")
    }

    "fail with a GeneralError when lifting none into dbprog with message" in {
      val datastore = MemDatastore.empty
      val error = datastore.execute(liftIntoDBProg(None, "blah")).run.leftValue
      error shouldBe an[GeneralError]
      error.message should include("blah")
    }

    "fail with a GeneralError when lifting a throwable into a DBProg" in {
      val datastore = MemDatastore.empty
      val ex = new Exception("I am an exception hear me roar!")
      val error = datastore.execute(liftIntoDBProg(ex.left)).run.leftValue
      error shouldBe an[GeneralError]
      error.message shouldBe ex.getMessage
    }

    "fail with a GeneralError when lifting a DBError into a DBProg" in {
      val datastore = MemDatastore.empty
      val error = liftDisjunction(ValueNotFound(Key("blah")).left).execute(datastore).run.leftValue
      error.message should include("blah")
    }
  }
}
