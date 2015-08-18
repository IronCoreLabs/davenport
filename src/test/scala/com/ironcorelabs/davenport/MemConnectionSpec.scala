//
// com.ironcorelabs.davenport.MemConnectionSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import org.scalatest.{ WordSpec, Matchers, BeforeAndAfterAll, OptionValues }
import org.typelevel.scalatest._
import DisjunctionValues._
import scala.language.postfixOps
import DB._

class MemConnectionSpec extends WordSpec with Matchers with BeforeAndAfterAll with DisjunctionMatchers with OptionValues {
  "MemConnection" should {
    import MemConnection.KVMap
    val k = Key("test")
    val v = RawJsonString("value")
    val newvalue = RawJsonString("some other value")
    val hv = MemConnection.genHashVer(v)
    val seedData: KVMap = Map(k -> v)
    val emptyData: KVMap = Map()
    val tenrows = (1 to 10).map { i =>
      (Key("key" + i) -> RawJsonString("val" + i))
    }
    // def tenrowdbs: DBBatchStream = tenrows.map(_.right[Throwable]).toIterator
    def tenrowdbs: DBBatchStream = tenrows.map(_.mapElements(k => liftIntoDBProg(k.right)).right[Throwable]).toIterator

    "reliably generate hashvers" ignore {
      // TODO!
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
      res should be(right)
      data should equal(seedData)
    }
    "fail to create a doc if it already exists" in {
      val testCreate = createDoc(k, newvalue)
      val (data, res) = MemConnection.run(testCreate, seedData)
      res should be(left)
      data should equal(seedData)
    }
    "update a doc that exists with correct hashver" in {
      val testUpdate = updateDoc(k, newvalue, hv)
      val (data, res) = MemConnection.run(testUpdate, seedData)
      res should be(right)
      data.get(k) should ===(Some(newvalue))
    }
    "fail updating a doc that doesn't exist" in {
      val testUpdate = updateDoc(k, newvalue, hv)
      val (data, res) = MemConnection.run(testUpdate)
      res should be(left)
      data should ===(emptyData)
    }
    "fail updating a doc when using incorrect hashver" in {
      val testUpdate = updateDoc(k, newvalue, HashVerString("badver"))
      val (data, res) = MemConnection.run(testUpdate, seedData)
      res should be(left)
      data should ===(seedData)
    }
    "remove a key that exists" in {
      val testRemove = removeKey(k)
      val (data, res) = MemConnection.run(testRemove, seedData)
      res should be(right)
      data should ===(emptyData)
    }
    "fail removing a key that doesn't exist" in {
      val testRemove = removeKey(k)
      val (data, res) = MemConnection.run(testRemove)
      res should be(left)
      data should ===(emptyData)
    }
    "modify map" in {
      val testModify = modifyDoc(k, j => newvalue)
      val (data, res) = MemConnection.run(testModify, seedData)
      res should be(right)
      data.get(k) should ===(Some(newvalue))
    }
    "modify map fails if key is not in db" in {
      val testModify = modifyDoc(k, j => newvalue)
      val (data, res) = MemConnection.run(testModify)
      res should be(left)
    }
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
    "be happy doing initial batch import" in {
      val res = MemConnection(batchCreateDocs(tenrowdbs))
      res.value.isThat should ===(true)
      res.value.onlyThat should ===((0 to 9).toList.toIList.some)
    }
    "return errors batch importing the same items again" in {
      val (data, res1) = MemConnection.run(batchCreateDocs(tenrowdbs))
      val res = MemConnection(batchCreateDocs(tenrowdbs), data)
      res should be(right)
      res.value.isThis should ===(true)
      res.value.onlyThis.value.length should ===(10)
    }
    "fail after first error if we pass in a halting function" in {
      val (data, res1) = MemConnection.run(batchCreateDocs(tenrowdbs))
      val res = MemConnection(batchCreateDocs(tenrowdbs, _ => false), data)
      res should be(right)
      res.value.isThis should ===(true)
      res.value.onlyThis.value.length should ===(1)
    }
    "return an error when we pass in an error in the stream" in {
      val err: DBBatchStream = Seq((new Throwable("bad input")).left).toIterator
      val res = MemConnection(batchCreateDocs(err))
      res should be(right)
      res.value.isThis should ===(true)
      res.value.onlyThis.value.length should ===(1)
    }
    "skip first 5 and return 5 errors" in {
      val res = MemConnection(batchCreateDocs(tenrowdbs.drop(5), _ => true))
      res should be(right)
      res.value.isThat should ===(true)
      res.value.onlyThat.value.length should ===(5)
    }
  }
}

