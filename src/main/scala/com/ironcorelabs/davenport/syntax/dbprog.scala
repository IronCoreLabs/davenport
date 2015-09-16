//
// com.ironcorelabs.davenport.dbprog
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import DB._
import scalaz.stream.Process
import scalaz._
import scalaz.concurrent.Task

object dbprog extends DBProgOps

trait DBProgOps {
  implicit class OurDBProgOps[A](self: DBProg[A]) {
    def process: Process[DBOps, Throwable \/ A] = Batch.liftToProcess(self)
    def interpretK: CouchInterpreter.CouchK[Throwable \/ A] = CouchInterpreter.interpretK(self)
    def interpret(i: CouchInterpreter): Task[Throwable \/ A] = i.interpret(self.run)
  }
}
