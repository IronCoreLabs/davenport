//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package interpreter

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz._
import DB._

trait Interpreter {
  //The only abstract method
  def interpret: (DBOps ~> Task)

  def interpret[A](db: DBProg[A]): Task[Throwable \/ A] = interpret(db.run)
  def interpretP[A](p: Process[DBOps, A]): Process[Task, A] = p.translate(interpret)
}
