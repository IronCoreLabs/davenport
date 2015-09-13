//
// com.ironcorelabs.davenport.MemConnection
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._
import scalaz.concurrent.Task
import DB._
import scalaz.stream.Process

/** Use an in-memory map to interpret DBOps */
object MemConnection {
  /** Dummy function to meet the abstract connection contract */
  def connect: Throwable \/ Unit = ().right
  /** Dummy function to meet the abstract connection contract */
  def disconnect(): Unit = ().right
  /** Dummy function to meet the abstract connection contract */
  def connected: Boolean = true
  /** Alias for apply, which does all of the work */
  def exec[A](db: DBProg[A]): Throwable \/ A = apply(db)
  /** Wrap results in a Task for further manipulation */
  def execTask[A](db: DBProg[A]): Task[Throwable \/ A] = Task.now(apply(db))

  /** Backend of the memory store is a Map from Key -> RawJsonString */
  type KVMap = Map[Key, RawJsonString]
  type KVState[A] = StateT[Task, KVMap, A]
  /** Arbitrary implementation of the hashver for records in the DB */
  def genHashVer(s: RawJsonString): HashVer =
    HashVer(scala.util.hashing.MurmurHash3.stringHash(s.value).toLong)

  /**
   * NT which brings DBOps to KVState. Useful for translating a Process[DBOps, A] into a Process[KVState, A].
   */
  val translateDBOps: DBOps ~> KVState = new (DBOps ~> KVState) {
    def apply[A](db: DBOps[A]): KVState[A] = {
      Free.runFC[DBOp, KVState, A](db)(toKVState)
    }
  }

  /**
   * NT which brings Task to a KVState. Useful for translating a Process[Task, A] into a Process[KVState, A].
   */
  val taskToKVState: Task ~> KVState = new (Task ~> KVState) {
    def apply[A](t: Task[A]): KVState[A] = {
      StateT { m2 => t.map(x => m2 -> x) }
    }
  }

  /**
   * Process demands that the type that it's being "run" into have a Catchable instance.
   * KVState has a logical one, but it has to be written manually.
   */
  implicit val myStateCatchable = new Catchable[KVState] {
    def attempt[A](f: KVState[A]): KVState[Throwable \/ A] = {
      f.map(a => Task(a).attemptRun)
    }
    def fail[A](err: Throwable): KVState[A] = {
      Hoist[StateT[?[_], KVMap, ?]].liftM(Catchable[Task].fail(err))
    }
  }

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

  /** Passes through to run, but returns only the result */
  def apply[A](prog: DBProg[A], m: KVMap = Map()): Throwable \/ A = run(prog, m)._2

  def runProcess[A](prog: Process[DBOps, A], m: KVMap = Map()): Throwable \/ (KVMap, IndexedSeq[A]) = {
    val process: Process[KVState, A] = prog.translate(translateDBOps)
    //Lint complained about Any being inferred on runLog so added the manual annotation.
    process.runLog[KVState, A].run(m).attemptRun
  }

  def translateProcess[A](prog: Process[DBOps, A]): Process[KVState, A] = {
    prog.translate(translateDBOps)
  }

  /**
   * Executes the DBProg and returns both the result and the Map showing db
   *  state
   */
  def run[A](prog: DBProg[A], m: KVMap = Map()): (KVMap, Throwable \/ A) = {
    //Run should be safe here because we're not actually doing anything with Task.
    Free.runFC[DBOp, KVState, Throwable \/ A](prog.run)(toKVState).apply(m).run
  }
}
