//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import org.scalacheck._
import org.scalacheck.Prop._
import Arbitrary.arbitrary
import DB._
import scalaz._, Scalaz._, scalaz.concurrent.Task
import argonaut._, Argonaut._
import datastore.MemDatastore
import syntax.key._

class DBDocumentSpec extends TestBase {
  case class User(firstName: String, lastName: String, email: String, createdDate: Long)
  object User {
    implicit def codec: CodecJson[User] = casecodec4(User.apply, User.unapply)(
      "firstName", "lastName", "email", "createdDate"
    )

    def genKey(u: User): Key = Key(s"user::${u.email}")
  }

  implicit def arbDBDocument[A: Arbitrary] = Arbitrary {
    for {
      keyString <- arbitrary[String]
      hashLong <- arbitrary[Long]
      a <- arbitrary[A]
    } yield DBDocument(Key(keyString), CommitVersion(hashLong), a)
  }

  "DBDocument" should {
    val u1 = User("first", "last", "email@example.com", 1440700748921L)
    val k1 = Key("user::email@example.com")
    "create, then get and then remove wrapper docs" in {
      val create = k1.dbCreate(u1)
      val datastore = MemDatastore.empty
      val res = datastore.execute(create).run
      res should be(right)
      // next line is basically to make sure commitVersion is populated and juice up
      // code coverage
      res.value.commitVersion.value should be > 0L

      val get = k1.dbGet[User]
      val res2 = datastore.execute(get).run.value
      res2.data should equal(u1)
      val res3 = datastore.execute(DBDocument.remove(res2.key)).run
      res3 should be(right)
    }
    "attempt removal of a missing doc" in {
      val res = MemDatastore.empty.execute(k1.dbRemove).run
      res should be(left) // fail since doesn't exist
    }

    "work correctly when constructing key from a counter" in {
      import syntax._
      val datastore = MemDatastore.empty
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
      counterKey.dbIncrementCounter(100).execute(datastore).run.value
      val notFoundError = getUserById.execute(datastore).run.leftValue
      notFoundError.message should include("user100")
      val createdUserDoc = createUserForId.execute(datastore).run.value
      createdUserDoc.data shouldBe u1
      getUserById.execute(datastore).run.value shouldBe createdUserDoc
    }

    "modify according to the passed in function" in {
      import syntax._
      val datastore = MemDatastore.empty
      val putUserDoc = k1.dbCreate(u1).execute(datastore).run.value
      def removeFirstName(u: User) = u.copy(firstName = "")
      val noMoreName = k1.dbModify[User](removeFirstName(_)).execute(datastore).run.value
      //value should be the same as the doc we put 
      noMoreName.data shouldBe putUserDoc.map(removeFirstName(_)).data
      //commit versions should *not* match because the data changed.
      noMoreName.commitVersion should not be (putUserDoc.commitVersion)
      //Get the data to be sure modify returned the correct data and commit version
      k1.dbGet[User].execute(datastore).run.value shouldBe noMoreName

    }

    "displays useful information on failed deserialization" in {
      import syntax._
      val datastore = MemDatastore.empty
      val putString = k1.dbCreate("hello").execute(datastore).run.value
      val dbError = k1.dbGet[User].execute(datastore).run.leftValue
      dbError match {
        case error @ DeserializationError(key, value, errorMessage) =>
          key shouldBe k1
          value shouldBe "\"hello\""
          errorMessage should include("(firstName)")
        case error =>
          fail(s"expected 'DeserializationError', but found  '$error' instead.")
      }
    }
    "have a lawful scalaz typeclasses" in {
      import scalaz.scalacheck.ScalazProperties
      check(ScalazProperties.equal.laws[DBDocument[Int]])
      check(ScalazProperties.functor.laws[DBDocument])
    }
  }
}
