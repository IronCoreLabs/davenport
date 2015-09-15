//
// com.ironcorelabs.davenport.process
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process
import MemInterpreter.{ KVState, KVMap }
import com.couchbase.client.java.Bucket
import DB.DBOps

object process extends ProcessOps

trait ProcessOps {
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
      f.map(a => \/.fromTryCatchNonFatal(a))
    }
    def fail[A](err: Throwable): KVState[A] = {
      Hoist[StateT[?[_], KVMap, ?]].liftM(Catchable[Task].fail(err))
    }
  }

  implicit class OurProcessOps[M[_], A](self: Process[M, A]) {
    def toKVState(implicit ev: Process[M, A] =:= Process[Task, A]): Process[KVState, A] =
      self.asInstanceOf[Process[Task, A]].translate(taskToKVState)

    def interpretMem(implicit ev: Process[M, A] =:= Process[DBOps, A]): Process[KVState, A] =
      MemInterpreter.interpretP(self)

    def interpretCouch(i: CouchInterpreter)(implicit ev: Process[M, A] =:= Process[DBOps, A]): Process[Task, A] =
      i.interpretP(self)
  }
}
