//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import DB._
import scalaz.stream.Process
import scalaz._
import scalaz.concurrent.Task
import interpreter.Interpreter

object dbprog extends DBProgOps

trait DBProgOps {
  implicit class OurDBProgOps[A](self: DBProg[A]) {
    def process: Process[DBOps, DBError \/ A] = Batch.liftToProcess(self)
    def interpret(i: Interpreter): Task[DBError \/ A] = i.interpret(self.run)
  }
}
