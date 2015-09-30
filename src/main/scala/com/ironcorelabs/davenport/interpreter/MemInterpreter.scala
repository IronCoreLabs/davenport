//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package interpreter

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz._
import scalaz.syntax.either._
import DB._

abstract class MemInterpreter extends Interpreter {
  import MemInterpreter._
  protected var map: KVMap

  def interpret: (DBOps ~> Task) = new (DBOps ~> Task) {
    def apply[A](prog: DBOps[A]): Task[A] = {
      //Note that the Task.delay captures the current state when this op is run, which is important
      //if you rerun a Task.
      Task.delay(map).flatMap(interpretKVState(prog)(_)).map {
        case (newM, value) =>
          //In order to provide the same semantics as Couch, once a value has been "computed" it will be 
          //committed to the DB. Note that this might overwrite someone elses changes in a multi-thread environment.
          map = newM
          value
      }
    }
  }
}

object MemInterpreter {
  /** Backend of the memory store is a Map from Key -> RawJsonString */
  type KVMap = Map[Key, RawJsonString]
  type KVState[A] = StateT[Task, KVMap, A]

  def apply(m: KVMap): MemInterpreter = new MemInterpreter {
    protected var map = m
  }

  def empty: MemInterpreter = apply(Map.empty)

  val interpretKVState: DBOps ~> KVState = new (DBOps ~> KVState) {
    def apply[A](db: DBOps[A]): KVState[A] = {
      Free.runFC[DBOp, KVState, A](db)(toKVState)
    }
  }

  /** Arbitrary implementation of the hashver for records in the DB */
  private[davenport] def genHashVer(s: RawJsonString): HashVer =
    HashVer(scala.util.hashing.MurmurHash3.stringHash(s.value).toLong)

  private def modifyState(s: KVMap): (KVMap, DBError \/ Unit) = s -> ().right
  private def modifyStateDbv(s: KVMap, j: RawJsonString, h: HashVer): (KVMap, DBError \/ DbValue) = s -> DbValue(j, h).right
  private def notFoundError[A](key: Key): DBError \/ A = ValueNotFound(key).left
  //Convienience method to lift f into KVState.
  private def state[A](f: KVMap => (KVMap, A)): KVState[A] = StateT[Task, KVMap, A] { map =>
    Task.delay(f(map))
  }
  private def toKVState: DBOp ~> KVState = new (DBOp ~> KVState) {
    def apply[A](op: DBOp[A]): KVState[A] = {
      op match {
        case GetDoc(k: Key) => state { m: KVMap =>
          m.get(k).map(json => m -> DbValue(json, genHashVer(json)).right)
            .getOrElse(m -> notFoundError(k))
        }
        case UpdateDoc(k, doc, hashver) => state { m: KVMap =>
          m.get(k).map { json =>
            val storedhashver = genHashVer(json)
            if (hashver == storedhashver) {
              modifyStateDbv(m + (k -> doc), doc, hashver)
            } else {
              m -> (HashMismatch(k).left)
            }
          }.getOrElse(m -> notFoundError(k))
        }
        case CreateDoc(k, doc) => state { m: KVMap =>
          m.get(k).map(_ => m -> ValueExists(k).left).getOrElse(modifyStateDbv(m + (k -> doc), doc, genHashVer(doc)))
        }
        case RemoveKey(k) => state { m: KVMap =>
          val keyOrError = m.get(k).map(_ => k.right).getOrElse(ValueNotFound(k).left)
          keyOrError.fold(t => m -> t.left, key => modifyState(m - k))
        }
        case GetCounter(k) => state { m: KVMap =>
          m.get(k).map { json =>
            try {
              (m -> json.value.toLong.right)
            } catch {
              case ex: Throwable => m -> GeneralError(ex).left
            }
          } getOrElse {
            (m + (k -> RawJsonString("0")) -> 0L.right)
          }
        }
        case IncrementCounter(k, delta) => state { m: KVMap =>
          m.get(k).map { json =>
            // convert to long and increment by delta
            try {
              val newval = json.value.toLong + delta
              (m + (k -> RawJsonString(newval.toString)) -> newval.right)
            } catch {
              case ex: Throwable => m -> GeneralError(ex).left
            }
          } getOrElse {
            // save delta to db
            (m + (k -> RawJsonString(delta.toString)), delta.right)
          }
        }
      }
    }
  }
}
