//
// com.ironcorelabs.davenport.CouchInterpreterSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package interpreter

import syntax._
import scalaz._, Scalaz._, scalaz.concurrent.Task, scalaz.stream.Process
import DB._
import DB.Batch._
import tags.RequiresCouch
import scala.concurrent.duration._
import org.scalatest.BeforeAndAfter

@RequiresCouch
class CouchInterpreterSpec extends InterpreterSpec with BeforeAndAfter {
  def interpreterName: String = "CouchInterpreterBasicTests"

  //Interpreter to test.
  def emptyInterpreter: Interpreter = CouchConnection.createInterpreter

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

  "CouchInterpreter" should {
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

    "work without using interpreter by instead using Kleisli" in {
      val createAndGet = for {
        _ <- createDoc(k, v)
        dbValue <- getDoc(k)
      } yield dbValue.data
      val task = CouchConnection.bucketOrError.flatMap(CouchInterpreter.interpretK(createAndGet).run(_))
      task.run.value shouldBe v
    }
  }
}
