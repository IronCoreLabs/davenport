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
  //Define how you map each op to a Task using a Natural Transformation.
  def interpret: (DBOps ~> Task)

  def interpret[A](db: DBProg[A]): Task[DBError \/ A] = interpret(db.run)
  def interpretP[A](p: Process[DBOps, A]): Process[Task, A] = p.translate(interpret)
}
