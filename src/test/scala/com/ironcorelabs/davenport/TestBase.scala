//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import org.scalatest.{ WordSpec, Matchers, OptionValues, BeforeAndAfterAll }
import org.scalatest.concurrent.AsyncAssertions
import org.typelevel.scalatest.{ DisjunctionMatchers, DisjunctionValues, ValidationMatchers }
import org.scalatest.prop.Checkers
import scalaz.\/

abstract class TestBase
    extends WordSpec
    with Matchers
    with OptionValues
    with DisjunctionMatchers
    with ValidationMatchers
    with DisjunctionValues
    with AsyncAssertions
    with BeforeAndAfterAll
    with Checkers {
  implicit class eitherOps[A, B](either: Either[A, B]) {
    def toDisjunction: A \/ B = \/.fromEither(either)
  }
}
