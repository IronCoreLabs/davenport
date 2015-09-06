//
// com.ironcorelabs.davenport.MemConnectionSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import org.scalatest.{ WordSpec, Matchers, BeforeAndAfterAll, OptionValues }
import org.scalatest.concurrent.AsyncAssertions
import org.typelevel.scalatest._
import DisjunctionValues._
import scala.language.postfixOps
import DB._
import DB.Batch._

class MemConnectionSpec extends WordSpec with Matchers with BeforeAndAfterAll with DisjunctionMatchers with OptionValues with AsyncAssertions {
  "MemConnection" should {

    //
    // Setup shared test data
    //

    import MemConnection.KVMap
    val k = Key("test")
    val v = RawJsonString("value")
    val newvalue = RawJsonString("some other value")
    val hv = MemConnection.genHashVer(v)
    val seedData: KVMap = Map(k -> v)
    val emptyData: KVMap = Map()
    val tenrows = (1 to 10).map { i =>
      (Key("key" + i) -> RawJsonString("val" + i))
    }.toList
    val fiveMoreRows = (20 until 25).map { i =>
      (Key("key" + i) -> RawJsonString("val" + i))
    }.toList

    //
    // Test basic create/get/update operations
    //

    "fake connect and disconnect" in {
      MemConnection.connect
      MemConnection.connected should be(true)
      MemConnection.disconnect
    }
    "get a doc that exists" in {
      val testRead = getDoc(k)
      val res = MemConnection(testRead, seedData)
      res.value.jsonString should ===(v)
    }
    "fail fetching a doc that doesn't exist" in {
      val testRead = getDoc(k)
      val res = MemConnection(testRead)
      res should be(left)
    }
    "create a doc that doesn't exist" in {
      val testCreate = createDoc(k, v)
      val (data, res) = MemConnection.run(testCreate)
      data should equal(seedData)
    }
    "work with an async call on task" in {
      val testCreate = createDoc(k, v)
      val w = new Waiter
      MemConnection.execAsync(testCreate, { res: Throwable \/ DbValue =>
        w {
          res.value should ===(DbValue(v, hv))
        }
        w.dismiss()
      })
      w.await()
    }
    "fail to create a doc if it already exists" in {
      val testCreate = createDoc(k, newvalue)
      MemConnection.run(testCreate, seedData)._2 should be(left)
    }
    "update a doc that exists with correct hashver" in {
      val testUpdate = updateDoc(k, newvalue, hv)
      val (data, res) = MemConnection.run(testUpdate, seedData)
      data.get(k) should ===(Some(newvalue))
    }
    "fail updating a doc that doesn't exist" in {
      val testUpdate = updateDoc(k, newvalue, hv)
      MemConnection.run(testUpdate)._2 should be(left)
    }
    "fail updating a doc when using incorrect hashver" in {
      val testUpdate = updateDoc(k, newvalue, HashVer(0))
      MemConnection.run(testUpdate, seedData)._2 should be(left)
    }
    "remove a key that exists" in {
      val testRemove = removeKey(k)
      val (data, res) = MemConnection.run(testRemove, seedData)
      data should ===(emptyData)
    }
    "fail removing a key that doesn't exist" in {
      val testRemove = removeKey(k)
      MemConnection.run(testRemove)._2 should be(left)
    }
    "modify map" in {
      val testModify = modifyDoc(k, j => newvalue)
      val (data, res) = MemConnection.run(testModify, seedData)
      data.get(k) should ===(Some(newvalue))
    }
    "modify map fails if key is not in db" in {
      val testModify = modifyDoc(k, j => newvalue)
      MemConnection.run(testModify)._2 should be(left)
    }

    //
    // Test counters
    //

    "get zero when retrieving non-existent counter" in {
      val (data, res) = MemConnection.run(getCounter(k))
      res.value should ===(0L)
    }
    "get 1 when incrementing non-existant counter with default delta" in {
      val (data, res) = MemConnection.run(incrementCounter(k))
      res.value should ===(1L)
    }
    "get 10 when incrementing existing counter (at 1) by 9" in {
      val (data, res) = MemConnection.run(for {
        _ <- incrementCounter(k)
        c <- incrementCounter(k, 9)
      } yield c)
      res.value should ===(10L)
    }
    "fail to get counter when counter is actually a string" in {
      val steps = for {
        _ <- createDoc(k, v)
        c <- getCounter(k)
      } yield c
      MemConnection(steps) should be(left)
    }
    "fail to increment counter when counter is actually a string" in {
      val steps = for {
        _ <- createDoc(k, v)
        c <- incrementCounter(k)
      } yield c
      MemConnection(steps) should be(left)
    }

    //
    // Test batch import
    //

    "be happy doing initial batch import" in {
      val (map, res) = MemConnection.runProcess(createDocs(tenrows)).value
      res.toList.separate._2.length should ===(tenrows.length)
    }
    "return errors batch importing the same items again" in {
      val (data, result) = MemConnection.runProcess(createDocs(tenrows)).value
      val p = createDocs(tenrows ++ fiveMoreRows)
      val (map, res) = MemConnection.runProcess(p, data).value
      res.length should ===(tenrows.length + fiveMoreRows.length)
      val (lefts, rights) = res.toList.separate
      lefts.length should ===(tenrows.length)
      rights.length should ===(fiveMoreRows.length)
    }
    "fail after on first error if we pass in a halting function" in {
      val (data, res1) = MemConnection.runProcess(createDocs(tenrows)).value
      val (_, res) = MemConnection.runProcess(createDocs(tenrows).takeWhile(_.isRight), data).value
      res.length should ===(0)
    }

    "don't try and insert first 5 and return 5 errors" in {
      val (data, res) = MemConnection.runProcess(createDocs(tenrows.drop(5))).value
      res.length should ===(5)
      res.toList.separate._2.length should ===(5)
    }

  }
}

