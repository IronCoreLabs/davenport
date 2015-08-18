//
// com.ironcorelabs.davenport.DBSpec
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import org.scalatest.{ WordSpec, Matchers, BeforeAndAfterAll }
import org.typelevel.scalatest._
import scala.language.postfixOps

class DBSpec extends WordSpec with Matchers with BeforeAndAfterAll with DisjunctionMatchers {
  "DB" should {
    // Honestly, I'm not sure what to test here. Better to run
    // tests at the interpreter, I think
  }
}
