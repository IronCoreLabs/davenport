//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import db._
import scalaz.stream.Process
import scalaz._
import scalaz.concurrent.Task
import datastore.Datastore

// The convention is for syntax objects to start with lower case, so they look
// like package names. Scalastyle doesn't care for this, so ignore the line.
final object dbprog extends DBProgOps // scalastyle:ignore

trait DBProgOps {
  implicit class OurDBProgOps[A](self: DBProg[A]) {
    def process: Process[DBOps, DBError \/ A] = batch.liftToProcess(self)
    def execute(d: Datastore): Task[DBError \/ A] = d.execute(self.run)
  }
}
