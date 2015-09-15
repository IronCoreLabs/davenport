//
// com.ironcorelabs.davenport.MemTranslator
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz._
import scalaz.syntax.either._
import DB._

object MemInterpreter {
  /** Backend of the memory store is a Map from Key -> RawJsonString */
  type KVMap = Map[Key, RawJsonString]
  type KVState[A] = StateT[Task, KVMap, A]

  def interpretTask[A](db: DBProg[A], initialState: KVMap = Map()): Task[(KVMap, Throwable \/ A)] =
    interpret(db)(initialState)

  def interpret[A](db: DBProg[A]): KVState[Throwable \/ A] =
    interpret(db.run)

  def interpret[A](db: DBOps[A]): KVState[A] =
    opsToKVState(db)

  def interpretP[A](p: Process[DBOps, A]): Process[KVState, A] = p.translate(opsToKVState)

  /**
   * NT which brings DBOps to KVState. Useful for translating a Process[DBOps, A] into a Process[KVState, A].
   */
  val opsToKVState: DBOps ~> KVState = new (DBOps ~> KVState) {
    def apply[A](db: DBOps[A]): KVState[A] = {
      Free.runFC[DBOp, KVState, A](db)(toKVState)
    }
  }

  /** Arbitrary implementation of the hashver for records in the DB */
  private[davenport] def genHashVer(s: RawJsonString): HashVer =
    HashVer(scala.util.hashing.MurmurHash3.stringHash(s.value).toLong)

  private def modifyState(s: KVMap): (KVMap, Throwable \/ Unit) = s -> ().right
  private def modifyStateDbv(s: KVMap, j: RawJsonString, h: HashVer): (KVMap, Throwable \/ DbValue) = s -> DbValue(j, h).right
  private def error[A](s: String): Throwable \/ A = (new Exception(s)).left
  //Convienience method to lift f into KVState.
  private def state[A](f: KVMap => (KVMap, A)): KVState[A] = StateT[Task, KVMap, A] { map =>
    Task.delay(f(map))
  }
  private def toKVState: DBOp ~> KVState = new (DBOp ~> KVState) {
    def apply[A](op: DBOp[A]): KVState[A] = {
      op match {
        case GetDoc(k: Key) => state { m: KVMap =>
          m.get(k).map(json => m -> DbValue(json, genHashVer(json)).right)
            .getOrElse(m -> error(s"No value found for key '${k.value}'"))
        }
        case UpdateDoc(k, doc, hashver) => state { m: KVMap =>
          m.get(k).map { json =>
            val storedhashver = genHashVer(json)
            if (hashver == storedhashver) {
              modifyStateDbv(m + (k -> doc), doc, hashver)
            } else {
              m -> error("Someone else updated this doc first")
            }
          }.getOrElse(m -> error(s"No value found for key '${k.value}'"))
        }
        case CreateDoc(k, doc) => state { m: KVMap =>
          m.get(k).map(_ => m -> error(s"Can't create since '${k.value}' already exists")).getOrElse(modifyStateDbv(m + (k -> doc), doc, genHashVer(doc)))
        }
        case RemoveKey(k) => state { m: KVMap =>
          val keyOrError = m.get(k).map(_ => k.right).getOrElse(error("Can't remove non-existent document"))
          keyOrError.fold(t => m -> t.left, key => modifyState(m - k))
        }
        case GetCounter(k) => state { m: KVMap =>
          m.get(k).map { json =>
            try {
              (m -> json.value.toLong.right)
            } catch {
              case _: Throwable => m -> error(s"Bad value in db for '${k.value}'")
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
              case _: Throwable => m -> error(s"Bad value in db for '${k.value}'")
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
