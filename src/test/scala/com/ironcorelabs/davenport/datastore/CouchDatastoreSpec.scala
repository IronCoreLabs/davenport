//
// com.ironcorelabs.davenport.CouchDatastoreSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import syntax._
import scalaz._, Scalaz._, scalaz.concurrent.Task, scalaz.stream.Process
import db._
import tags.RequiresCouch
import scala.concurrent.duration._
import org.scalatest.BeforeAndAfter

@RequiresCouch
class CouchDatastoreSpec extends DatastoreSpec with BeforeAndAfter {
  def datastoreName: String = "CouchDatastoreBasicTests"

  def emptyDatastore: Datastore = CouchConnection.createDatastore

  override def beforeAll() = {
    CouchConnection.connect
    ()
  }

  override def afterAll() = {
    CouchConnection.disconnect;
    ()
  }

  /**
   * Before each test, cleanup all the test data we use for the tests. This means
   * every key that is used in the tests needs to be in the cleanup function.
   */
  before {
    cleanup
  }

  def cleanup() = {
    val keys = k :: (tenrows ++ fiveMoreRows).map { case (k, _) => k }
    keys.foreach { k =>
      run(removeKey(k))
    }
  }

  "CouchDatastore" should {
    "handle a failed connection" in {
      // Save off bucket and then ditch it
      CouchConnection.fakeDisconnect

      // Prove that the connection fails
      val connectionfail = createTask(getDoc(k))
      connectionfail.attemptRun.leftValue.getMessage should ===("Not connected")

      CouchConnection.fakeDisconnectRevert
    }
    "simulate a connection failure and recover from it using Task retry" in {
      // handle async fun
      val w = new Waiter

      // Save off bucket and then hide it
      CouchConnection.fakeDisconnect

      val connectionfail = createTask(getDoc(k))

      // Setup a retry.  Within the retry, resolve the problem
      val retry = connectionfail.retryAccumulating(Seq(155.millis, 1025.millis), { t =>
        CouchConnection.fakeDisconnectRevert
        t.getMessage == "Not connected" // return true to retry if not connected
      })
      val res = retry.attemptRun
      res should be(right)
    }

    "work without using datastore by instead using Kleisli" in {
      val createAndGet = for {
        _ <- createDoc(k, v)
        dbValue <- getDoc(k)
      } yield dbValue.data
      val task = CouchConnection.bucketOrError.flatMap(CouchDatastore.executeK(createAndGet).run(_))
      task.run.value shouldBe v
    }
  }
}
