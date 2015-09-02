//
// com.ironcorelabs.davenport.DBDocumentSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import org.scalatest.{ WordSpec, Matchers, OptionValues }
import scala.language.postfixOps
import org.typelevel.scalatest._
import DisjunctionValues._
import com.ironcorelabs.davenport._, DB._
import scalaz._, Scalaz._, scalaz.concurrent.Task
import argonaut._, Argonaut._

class DBDocumentSpec extends WordSpec with Matchers with DisjunctionMatchers with OptionValues {
  case class User(firstName: String, lastName: String, email: String, createdDate: Long)
  case class DBUser(key: Key, data: User, cas: Long) extends DBDocument[User] {
    def dataJson: Throwable \/ RawJsonString =
      \/.fromTryCatchNonFatal(DBUser.toJsonString(data)(DBUser.codec))
  }
  object DBUser extends DBDocumentCompanion[User] {
    implicit def codec: CodecJson[User] = casecodec4(User.apply, User.unapply)(
      "firstName", "lastName", "email", "createdDate"
    )
    def genKey(u: User): DBProg[Key] = liftIntoDBProg(Key(s"user::${u.email}").right[Throwable])
    def fromJson(s: RawJsonString): Throwable \/ User =
      fromJsonString(s.value) \/> new Exception("Failed to decode json to User")
    def create(u: User): DBProg[DBUser] = for {
      json <- liftIntoDBProg(\/.fromTryCatchNonFatal(toJsonString(u)))
      key <- genKey(u)
      newdoc <- createDoc(key, json)
      cas = newdoc.hashVer.value
    } yield DBUser(key, u, cas)
    def get(key: Key): DBProg[DBUser] = for {
      doc <- getDoc(key)
      u <- liftIntoDBProg(fromJson(doc.jsonString))
      cas = doc.hashVer.value
    } yield DBUser(key, u, cas)
  }

  "DBDocument" should {
    val u1 = User("first", "last", "email@example.com", 1440700748921L)
    val k1 = Key("user::email@example.com")
    "create, then get and then remove wrapper docs" in {
      val create = DBUser.create(u1)
      val (data, res) = MemConnection.run(create)

      // next line is basically to make sure hashver is populated and juice up
      // code coverage
      res.value.hashver.value should be > 0L

      val get = DBUser.get(k1)
      val (data2, res2) = MemConnection.run(get, data)
      res2.value.data should equal(u1)
      val (data3, res3) = MemConnection.run(res2.value.remove, data)
    }
    "attempt removal of a missing doc" in {
      MemConnection(DBUser.remove(k1)) should be(left) // fail since doesn't exist
    }
  }
}
