//
// com.ironcorelabs.davenport.MemDatastoreSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package datastore

import syntax._
import scalaz._, Scalaz._, scalaz.concurrent.Task
import DB._
import DB.Batch._
import scalaz.stream.Process

class MemDatastoreSpec extends DatastoreSpec {
  def datastoreName: String = "MemDatastore"
  def emptyDatastore: Datastore = MemDatastore.empty
}
