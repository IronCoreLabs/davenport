//
// com.ironcorelabs.davenport.MemInterpreterSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package interpreter

import syntax._
import scalaz._, Scalaz._, scalaz.concurrent.Task
import DB._
import DB.Batch._
import MemInterpreter.{ KVMap, KVState }
import scalaz.stream.Process

class MemInterpreterSpec extends InterpreterSpec {
  def interpreterName: String = "MemInterpreter"
  def emptyInterpreter: Interpreter = MemInterpreter.empty
}
