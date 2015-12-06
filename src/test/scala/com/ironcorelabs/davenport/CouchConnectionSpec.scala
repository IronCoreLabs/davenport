//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task, scalaz.stream.Process
import db._
import tags.RequiresCouch
import syntax.dbprog._
import scala.concurrent.duration._

@RequiresCouch
class CouchConnectionSpec extends TestBase {
  val davenportConfig = DavenportConfig.withDefaults //COLT: Read with Knobs.
  var connection: CouchConnection = null

  override def beforeAll() = {
    connection = CouchConnection(davenportConfig)
    ()
  }

  override def afterAll() = {
    connection.disconnect.attemptRun.value
    ()
  }

  "CouchConnection" should {
    "handle a failed connection" in {
      //Disconnect our connection.
      connection.disconnect.attemptRun.value
      // Prove that the connection fails
      val connectionfail = db.getDoc(Key("a")).execute(connection.openDatastore(BucketNameAndPassword("default", None)))
      connectionfail.attemptRun.leftValue shouldBe a[DisconnectedException]
      connection = CouchConnection(davenportConfig)
    }
    "allow closing buckets if they're open" in {
      val b = BucketNameAndPassword("default", None)
      val openedBucket = connection.openBucket(b).attemptRun.value
      openedBucket.name shouldBe b.name
      connection.openBuckets.get(b).value shouldBe openedBucket
      //Close should succeed
      val closeTask = connection.closeBucket(b)
      closeTask.attemptRun should beRight(true)
      //The bucket shouldn't be there anymore
      connection.openBuckets.get(b) shouldBe None
      //The close still suceeds if closing something that isn't there, but it'll be false.
      closeTask.attemptRun should beRight(false)
    }
  }
}
