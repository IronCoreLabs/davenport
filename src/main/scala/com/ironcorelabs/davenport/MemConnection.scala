//
// com.ironcorelabs.davenport.MemConnection
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._
import scalaz.concurrent.Task
import DB._

/** Use an in-memory map to interpret DBOps */
object MemConnection extends AbstractConnection {
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
  /** Use scalaz state to safely evolve the map for most operations */
  type KVState[A] = State[KVMap, A]

  /** Arbitrary implementation of the hashver for records in the DB */
  def genHashVer(s: RawJsonString): HashVer =
    HashVer(scala.util.hashing.MurmurHash3.stringHash(s.value).toLong)

  private def modifyState(s: KVMap): (KVMap, Throwable \/ Unit) = s -> ().right
  private def modifyStateDbv(s: KVMap, j: RawJsonString, h: HashVer): (KVMap, Throwable \/ DbValue) = s -> DbValue(j, h).right
  private def error[A](s: String): Throwable \/ A = (new Exception(s)).left
  private def toKVState: DBOp ~> KVState = new (DBOp ~> KVState) {
    def apply[A](op: DBOp[A]): KVState[A] = {
      op match {
        case GetDoc(k: Key) => State.get.map { m: KVMap =>
          m.get(k).map(json => DbValue(json, genHashVer(json)).right)
            .getOrElse(error(s"No value found for key '${k.value}'"))
        }
        case UpdateDoc(k, doc, hashver) => State { m: KVMap =>
          m.get(k).map { json =>
            val storedhashver = genHashVer(json)
            if (hashver == storedhashver) {
              modifyStateDbv(m + (k -> doc), doc, hashver)
            } else {
              m -> error("Someone else updated this doc first")
            }
          }.getOrElse(m -> error(s"No value found for key '${k.value}'"))
        }
        case CreateDoc(k, doc) => State { m: KVMap =>
          m.get(k).map(_ => m -> error(s"Can't create since '${k.value}' already exists")).getOrElse(modifyStateDbv(m + (k -> doc), doc, genHashVer(doc)))
        }
        case RemoveKey(k) => State { m: KVMap =>
          val keyOrError = m.get(k).map(_ => k.right).getOrElse(error("Can't remove non-existent document"))
          keyOrError.fold(t => m -> t.left, key => modifyState(m - k))
        }
        case GetCounter(k) => State { m: KVMap =>
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
        case IncrementCounter(k, delta) => State { m: KVMap =>
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
        case BatchCreateDocs(st: DBBatchStream, continue: (Throwable => Boolean)) => State { m: KVMap =>
          {
            // Sadly, we're using a mutable map internally here, seeded
            // with anything passed in and returned later as new immutable
            // state
            var tmpMap: scala.collection.mutable.Map[Key, RawJsonString] =
              scala.collection.mutable.Map() ++ m

            val emptyResult: DBBatchResults =
              IList[Int]().wrapThat[IList[(Int, Throwable)]]

            // TODO: get smart and flatmap this stuff below. or peal out to
            // functions
            val tdbres: Iterator[DBBatchResults] = st.zipWithIndex.map {
              (altogether: (Throwable \/ (DBProg[Key], RawJsonString), Int)) =>
                {
                  val (record, idx) = altogether
                  disj2BatchResult(record, idx, { progKeyAndV: (DBProg[Key], RawJsonString) =>
                    val (edbpk, doc) = progKeyAndV
                    disj2BatchResult(exec(edbpk), idx, { k: Key =>
                      tmpMap.get(k) match {
                        case Some(_) => batchFailed(idx, new Throwable(s"Can't create since '${k.value}' already exists"))
                        case None => {
                          tmpMap += (k -> doc)
                          batchSucceeded(idx)
                        }
                      }
                    })
                  })
                }
            }

            // Note: the tmpMap doesn't mutate until after we call reduceOption
            //       (since we're mapping on an iterator and so everything is lazy)
            val res = stopIteratingWhenContinueFunctionFails(tdbres, continue)
              .reduceOption(_ |+| _)
              .getOrElse(emptyResult).right

            tmpMap.toMap -> res
          }
        }
      }
    }

    /*
     * Helpers for the grammar interpreter
     */
    private def disj2BatchResult[A](res: Throwable \/ A, idx: Int, f: A => DBBatchResults): DBBatchResults =
      res.fold(e => batchFailed(idx, e), a => f(a))

    private def stopIteratingWhenContinueFunctionFails(st: Iterator[DBBatchResults], continue: Throwable => Boolean): Iterator[DBBatchResults] = {
      // For continuation, we want to include results from the first error
      // even if we cancel at that time, which is tricky and sadly
      // requires a bit of mutability
      var lastLineAndError = none[DBBatchResults]
      st.takeWhile {
        case \&/.This(ilist: IList[(Int, Throwable)]) => ilist.headOption.fold(true) {
          case (idx, e) => continue(e) || {
            lastLineAndError = batchFailed(idx, e).some
            false
          }
          case _ => true
        }
        case _ => true
        // hack to return the last error when the continue function
        // aborts further processing (takeWhile won't return it)
      } ++ lastLineAndError.toIterator
    }
  }

  /** Passes through to run, but returns only the result */
  def apply[A](prog: DBProg[A], m: KVMap = Map()): Throwable \/ A = run(prog, m)._2

  /**
   * Executes the DBProg and returns both the result and the Map showing db
   *  state
   */
  def run[A](prog: DBProg[A], m: KVMap = Map()): (KVMap, Throwable \/ A) =
    Free.runFC[DBOp, KVState, Throwable \/ A](prog.run)(toKVState).apply(m)
}
