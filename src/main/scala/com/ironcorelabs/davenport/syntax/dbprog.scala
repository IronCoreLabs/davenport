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

// The convention is for syntax objects to start with lower case, so they look
// like package names. Scalastyle doesn't care for this, so ignore the line.
final object dbprog extends DBProgOps // scalastyle:ignore

trait DBProgOps {
  implicit class OurDBProgOps[A](self: DBProg[A]) {
    def process: Process[DBOps, DBError \/ A] = Batch.liftToProcess(self)
    def interpret(i: Interpreter): Task[DBError \/ A] = i.interpret(self.run)
  }
}
