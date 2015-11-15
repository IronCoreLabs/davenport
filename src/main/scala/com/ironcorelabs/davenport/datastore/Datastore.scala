//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz._
import DB._

trait Datastore {
  //Define how you map each op to a Task using a Natural Transformation.
  def execute: (DBOps ~> Task)

  def execute[A](db: DBProg[A]): Task[DBError \/ A] = execute(db.run)
  def executeP[A](p: Process[DBOps, A]): Process[Task, A] = p.translate(execute)
}
