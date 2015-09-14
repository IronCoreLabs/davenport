//
// com.ironcorelabs.davenport.CouchConnection
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package syntax

import DB._
import scalaz.stream.Process
import scalaz._
import scalaz.concurrent.Task
import com.couchbase.client.java.Bucket

object dbprog extends DBProgOps

trait DBProgOps {
  implicit class OurDBProgOps[A](self: DBProg[A]) {

    def process: Process[DBOps, Throwable \/ A] = Batch.liftToProcess(self)
    def interpretCouchK: CouchTranslator.CouchK[Throwable \/ A] = CouchTranslator.interpretK(self)
    def interpretCouch(b: Bucket): Task[Throwable \/ A] = CouchTranslator.interpret(b)(self.run)
  }
}
