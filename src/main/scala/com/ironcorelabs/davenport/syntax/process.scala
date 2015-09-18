//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process
import com.couchbase.client.java.Bucket
import DB.DBOps
import interpreter.Interpreter

object process extends ProcessOps

trait ProcessOps {
  implicit class OurProcessOps[M[_], A](self: Process[M, A]) {
    def interpret(i: Interpreter)(implicit ev: Process[M, A] =:= Process[DBOps, A]): Process[Task, A] =
      i.interpretP(self)
  }
}
