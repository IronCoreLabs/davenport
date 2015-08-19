//
// com.ironcorelabs.davenport.CouchConnectionSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import org.scalatest.{ WordSpec, Matchers, BeforeAndAfterAll, OptionValues, Tag }
import org.typelevel.scalatest._
import DisjunctionValues._
import scala.language.postfixOps
import DB._
import com.ironcorelabs.davenport.tags.RequiresCouch
// import com.ironcorelabs.davenport.tagobjects.RequiresCouch
import scala.concurrent.duration._

@RequiresCouch
class CouchConnectionSpec extends WordSpec with Matchers with BeforeAndAfterAll with DisjunctionMatchers with OptionValues {
  val k = Key("test")
  val k404 = Key("test404")
  val v = RawJsonString("value")
  val newvalue = RawJsonString("some other value")
  val tenrows = (1 to 10).map { i =>
    (Key("key" + i) -> RawJsonString("val" + i))
  }
  def tenrowdbs: DBBatchStream = tenrows.map(_.mapElements(k => liftIntoDBProg(k.right)).right[Throwable]).toIterator

  override def beforeAll() = {
    // Connect and make sure test key is not in db in case of
    // bad cleanup on last run
    CouchConnection.connect
    cleanup
    ()
  }

  override def afterAll() = {
    cleanup
    CouchConnection.disconnect;
    ()
  }

  def cleanup() = {
    CouchConnection(removeKey(k))
    tenrows.foreach(kv => CouchConnection(removeKey(kv._1)))
  }

  "CouchConnection" should {
    import java.util._
    import com.couchbase.client.java.error._
    "fail fetching a doc that doesn't exist" in {
      val res = CouchConnection(getDoc(k))
      res.leftValue.getClass should ===(classOf[DocumentDoesNotExistException])
    }
    "create a doc that doesn't exist" in {
      val res = CouchConnection(createDoc(k, v))
      res.value.jsonString should ===(v)
    }
    "get a doc that should now exist" in {
      val res = CouchConnection(getDoc(k))
      res.value.jsonString should ===(v)
    }
    "fail to create a doc if it already exists" in {
      val testCreate = createDoc(k, newvalue)
      val res = CouchConnection(testCreate)
      res.leftValue.getClass should ===(classOf[DocumentAlreadyExistsException])
    }
    "update a doc that exists with correct hashver" in {
      val testUpdate = for {
        t <- getDoc(k)
        res <- updateDoc(k, newvalue, t.hashVer)
      } yield res
      val res = CouchConnection(testUpdate)
      res should be(right)
      val res2 = CouchConnection(getDoc(k))
      res2.value.jsonString should ===(newvalue)
    }
    "fail updating a doc that doesn't exist" in {
      val testUpdate = updateDoc(k404, newvalue, HashVer(1234))
      val res = CouchConnection(testUpdate)
      res.leftValue.getClass should ===(classOf[DocumentDoesNotExistException])
    }
    "fail updating a doc when using incorrect hashver" in {
      val testUpdate = updateDoc(k, v, HashVer(1234))
      val res = CouchConnection(testUpdate)
      res.leftValue.getClass should ===(classOf[CASMismatchException])
    }
    "remove a key that exists" in {
      val testRemove = removeKey(k)
      val res = CouchConnection(removeKey(k))
      res should be(right)
    }
    "fail removing a key that doesn't exist" in {
      val testRemove = removeKey(k)
      val res = CouchConnection(removeKey(k))
      res.leftValue.getClass should ===(classOf[DocumentDoesNotExistException])
    }
    "modify map fails if key is not in db" in {
      val testModify = modifyDoc(k, j => newvalue)
      val res = CouchConnection(testModify)
      res should be(left)
    }
    "modify map after create" in {
      val testModify = for {
        _ <- createDoc(k, v)
        res <- modifyDoc(k, j => newvalue)
      } yield res
      val res = CouchConnection(testModify)
      res.value.jsonString should ===(newvalue)
    }
    "get zero when retrieving non-existent counter" in {
      val res = CouchConnection(for {
        _ <- removeKey(k) // make sure it is gone
        c <- getCounter(k)
        _ <- removeKey(k) // clean up
      } yield c)
      res.value should ===(0L)
    }
    "get 1 when incrementing non-existant counter with default delta" in {
      val res = CouchConnection(incrementCounter(k))
      res.value should ===(1L)
    }
    "get 10 when incrementing existing counter (at 1) by 9" in {
      val res = CouchConnection(incrementCounter(k, 9))
      res.value should ===(10L)
    }
    "still get 10 when fetching existing counter" in {
      val res = CouchConnection(getCounter(k))
      res.value should ===(10L)
    }
    "be happy doing initial batch import" in {
      val res = CouchConnection(batchCreateDocs(tenrowdbs))
      res.value.isThat should ===(true)
      res.value.onlyThat should ===((0 to 9).toList.toIList.some)
    }
    "return errors batch importing the same items again" in {
      val res = CouchConnection(batchCreateDocs(tenrowdbs))
      res should be(right)
      res.value.isThis should ===(true)
      res.value.onlyThis.get.length should ===(10)
      // OK, yeah, this is damn ugly.  Let me break it down.
      // res is Throwable \/ DBBatchResults. We shouldn't ever
      // have a .left here, but the way I built the system, we have
      // to have a disjunction. Sorry.
      // Then we have the DBBatchResults, which is one of This, That
      // or Both. For this test, we're expecting only to have This, which
      // is essentially the Left. We extract this left side with onlyThis,
      // which returns an option. So we use .get on that. Then we have an
      // IList of index and exception pairs. But IList doesn't have a forall
      // method so we convert to list. Inside the forall we look to _2,
      // which is the error. Ugly, but there you go.
      res.value.onlyThis.get.toList.forall(_._2.getClass == classOf[DocumentAlreadyExistsException]) should ===(true)
    }
    "fail after first error if we pass in a halting function" in {
      val res = CouchConnection(batchCreateDocs(tenrowdbs, _ => false))
      res should be(right)
      res.value.isThis should ===(true)
      res.value.onlyThis.value.length should ===(1)
    }
    "skip first 5 and return 5 errors" in {
      val res = CouchConnection(batchCreateDocs(tenrowdbs.drop(5), _ => true))
      res should be(right)
      res.value.isThis should ===(true)
      res.value.onlyThis.value.length should ===(5)
    }
    "return an error when we pass in an error in the stream" in {
      val err: DBBatchStream = Seq((new Throwable("bad input")).left).toIterator
      val res = CouchConnection(batchCreateDocs(err))
      res should be(right)
      res.value.isThis should ===(true)
      res.value.onlyThis.value.length should ===(1)
    }

    "attempt to recover from a bad CAS error by refetching and retrying" in {
      // Setup
      CouchConnection(for {
        _ <- removeKey(k)
        _ <- createDoc(k, v)
      } yield ())

      // Update
      def upd(cas: Long) = updateDoc(k, newvalue, HashVer(cas))

      // Generate a task that will fail with a bad CAS and prove it
      val res: Task[Throwable \/ DbValue] = CouchConnection.execTask(upd(123))
      res.attemptRun.join should be(left)

      // Generate a task that handles CAS errors and run and verify
      val handled: Task[Throwable \/ DbValue] = res.handleWith {
        case e: CASMismatchException => CouchConnection.execTask(for {
          v <- getDoc(k)
          u <- upd(v.hashVer.value)
        } yield u)
      }
      val finalres = handled.attemptRun.join
      finalres.value.jsonString should ===(newvalue)
    }
    /*
     * TODO: No idea why this doesn't work. Punting until later.
     */
    "simulate a connection failure and recover from it" ignore {
      // Save off bucket and then ditch it
      CouchConnection.fakeDisconnect

      // Prove that the connection fails
      val connectionfail = CouchConnection.execTask(getDoc(k))
      connectionfail.attemptRun.join.leftValue.getMessage should ===("Not connected")

      // Setup a retry.  Within the retry, resolve the problem
      val retry = connectionfail.retry(Seq(155.millis, 125.millis, 100.millis), { t =>
        println("***** Retrying")
        CouchConnection.fakeDisconnectRevert
        t.getMessage == "Not connected" // return true to retry if not connected
      })
      retry.attemptRun.join should be(right)
    }
  }
}
