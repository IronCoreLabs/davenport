//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import DB._
import scalaz._, Scalaz._, scalaz.concurrent.Task
import argonaut._, Argonaut._
import interpreter.MemInterpreter
import documentSyntax._

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
      u1.create(k1)
      val create = Document.create(k1, u1)
      val interpreter = MemInterpreter(Map())
      val res = interpreter.interpret(create).run
      res should be(right)
      // next line is basically to make sure hashver is populated and juice up
      // code coverage
      res.value.hashVer.value should be > 0L

      val get = Document.get[User](k1)
      val res2 = interpreter.interpret(get).run.value
      res2.data.value should equal(u1)
      val res3 = interpreter.interpret(Document.remove(res2.key)).run
      res3 should be(right)
    }
    "attempt removal of a missing doc" in {
      val res = MemInterpreter(Map()).interpret(Document.remove(k1)).run
      res should be(left) // fail since doesn't exist
    }
  }
}
