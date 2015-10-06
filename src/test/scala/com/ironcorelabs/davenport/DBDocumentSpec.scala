//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import DB._
import scalaz._, Scalaz._, scalaz.concurrent.Task
import argonaut._, Argonaut._
import interpreter.MemInterpreter
import syntax.key._

class DBDocumentSpec extends TestBase {
  case class User(firstName: String, lastName: String, email: String, createdDate: Long)
  object User {
    implicit def codec: CodecJson[User] = casecodec4(User.apply, User.unapply)(
      "firstName", "lastName", "email", "createdDate"
    )

    def genKey(u: User): Key = Key(s"user::${u.email}")
  }

  "DBDocument" should {
    val u1 = User("first", "last", "email@example.com", 1440700748921L)
    val k1 = Key("user::email@example.com")
    "create, then get and then remove wrapper docs" in {
      val create = k1.dbCreate(u1)
      val interpreter = MemInterpreter.empty
      val res = interpreter.interpret(create).run
      res should be(right)
      // next line is basically to make sure hashver is populated and juice up
      // code coverage
      res.value.hashVer.value should be > 0L

      val get = k1.dbGet[User]
      val res2 = interpreter.interpret(get).run.value
      res2.data should equal(u1)
      val res3 = interpreter.interpret(DBDocument.remove(res2.key)).run
      res3 should be(right)
    }
    "attempt removal of a missing doc" in {
      val res = MemInterpreter.empty.interpret(k1.dbRemove).run
      res should be(left) // fail since doesn't exist
    }

    "key from a counter should interface with Document" in {
      import syntax._
      val interpreter = MemInterpreter.empty
      val counterKey = Key("myCounter")
      val getUserById = for {
        k <- counterKey.dbGetCounter
        user <- Key(s"user$k").dbGet[User]
      } yield user

      val createUserForId = for {
        k <- counterKey.dbIncrementCounter(1)
        user <- Key(s"user$k").dbCreate(u1)
      } yield user

      //Make the value 100 just for fun.
      counterKey.dbIncrementCounter(100).interpret(interpreter).run.value
      val notFoundError = getUserById.interpret(interpreter).run.leftValue
      notFoundError.message should include("user100")
      val createdUserDoc = createUserForId.interpret(interpreter).run.value
      createdUserDoc.data shouldBe u1
      getUserById.interpret(interpreter).run.value shouldBe createdUserDoc
    }
  }
}
