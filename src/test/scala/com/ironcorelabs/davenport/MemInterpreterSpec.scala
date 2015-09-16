//
// com.ironcorelabs.davenport.MemInterpreterSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import DB._
import DB.Batch._
import MemInterpreter.{ KVMap, KVState, interpretTask }
import syntax._
import scalaz.stream.Process

class MemInterpreterSpec extends TestBase {
  //Variables used in many tests
  val k = Key("test")
  val v = RawJsonString("value")
  val newvalue = RawJsonString("some other value")
  val hv = MemInterpreter.genHashVer(v)
  val seedData: KVMap = Map(k -> v)
  val emptyData: KVMap = Map()
  val tenrows = (1 to 10).map { i =>
    (Key("key" + i) -> RawJsonString("val" + i))
  }.toList
  val fiveMoreRows = (20 until 25).map { i =>
    (Key("key" + i) -> RawJsonString("val" + i))
  }.toList

  //Couple helper functions
  def run[A](prog: DBProg[A], m: KVMap = Map()): (KVMap, Throwable \/ A) = {
    MemInterpreter.interpret(prog).apply(m).run
  }

  def runProcess[A](process: Process[DBOps, A], m: KVMap = Map()): Throwable \/ (KVMap, IndexedSeq[A]) = {
    val resultingKVState = process.interpretMem.runLog
    resultingKVState(m).attemptRun
  }

  def runForResult[A](prog: DBProg[A], m: KVMap = Map()): Throwable \/ A =
    run(prog, m)._2

  "MemInterpreter" should {
    //
    // Test basic create/get/update operations
    //
    "get a doc that exists" in {
      val testRead = getDoc(k)
      val res = runForResult(testRead, seedData)
      res.value.jsonString should ===(v)
    }
    "fail fetching a doc that doesn't exist" in {
      val testRead = getDoc(k)
      val res = runForResult(testRead)
      res should be(left)
    }
    "create a doc that doesn't exist" in {
      val testCreate = createDoc(k, v)
      val (data, res) = interpretTask(testCreate).run
      data should equal(seedData)
    }
    "fail to create a doc if it already exists" in {
      val testCreate = createDoc(k, newvalue)
      MemInterpreter.interpretTask(testCreate, seedData).run._2 should be(left)
    }
    "update a doc that exists with correct hashver" in {
      val testUpdate = updateDoc(k, newvalue, hv)
      val (data, res) = run(testUpdate, seedData)
      data.get(k) should ===(Some(newvalue))
    }
    "fail updating a doc that doesn't exist" in {
      val testUpdate = updateDoc(k, newvalue, hv)
      val (data, res) = run(testUpdate)
      data should ===(emptyData)
      res should be(left)
    }
    "fail updating a doc when using incorrect hashver" in {
      val testUpdate = updateDoc(k, newvalue, HashVer(0))
      val (data, res) = run(testUpdate, seedData)
      data should ===(seedData)
      res should be(left)
    }
    "remove a key that exists" in {
      val testRemove = removeKey(k)
      val (data, res) = run(testRemove, seedData)
      data should ===(emptyData)
    }
    "fail removing a key that doesn't exist" in {
      val testRemove = removeKey(k)
      val (data, res) = run(testRemove)
      data should ===(emptyData)
      res should be(left)
    }
    "modify map" in {
      val testModify = modifyDoc(k, j => newvalue)
      val (data, res) = run(testModify, seedData)
      data.get(k) should ===(Some(newvalue))
    }
    "modify map fails if key is not in db" in {
      val testModify = modifyDoc(k, j => newvalue)
      val (data, res) = run(testModify)
      data should ===(emptyData)
      res should be(left)
    }

    //
    // Test counters
    //
    "get zero when retrieving non-existent counter" in {
      val (data, res) = run(getCounter(k))
      res.value should ===(0L)
    }
    "get 1 when incrementing non-existant counter with default delta" in {
      val (data, res) = run(incrementCounter(k))
      res.value should ===(1L)
    }
    "get 10 when incrementing existing counter (at 1) by 9" in {
      val (data, res) = run(for {
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
      runForResult(steps) should be(left)
    }
    "fail to increment counter when counter is actually a string" in {
      val steps = for {
        _ <- createDoc(k, v)
        c <- incrementCounter(k)
      } yield c
      runForResult(steps) should be(left)
    }

    //
    // Test batch import
    //
    "be happy doing initial batch import" in {
      val (map, res) = runProcess(createDocs(tenrows)).value
      res.toList.separate._2.length should ===(tenrows.length)
    }
    "return errors batch importing the same items again" in {
      val (data, result) = runProcess(createDocs(tenrows)).value
      val p = createDocs(tenrows ++ fiveMoreRows)
      val (map, res) = runProcess(p, data).value
      res.length should ===(tenrows.length + fiveMoreRows.length)
      val (lefts, rights) = res.toList.separate
      lefts.length should ===(tenrows.length)
      rights.length should ===(fiveMoreRows.length)
    }
    "fail after on first error if we pass in a halting function" in {
      val (data, res1) = runProcess(createDocs(tenrows)).value
      val (_, res) = runProcess(createDocs(tenrows).takeWhile(_.isRight), data).value
      res.length should ===(0)
    }

    "don't try and insert first 5 and return 5 errors" in {
      val (data, res) = runProcess(createDocs(tenrows.drop(5))).value
      res.length should ===(5)
      res.toList.separate._2.length should ===(5)
    }
  }
}
