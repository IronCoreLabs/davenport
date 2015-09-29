//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import org.scalatest.{ WordSpec, Matchers, OptionValues, BeforeAndAfterAll }
import org.scalatest.concurrent.AsyncAssertions
import org.typelevel.scalatest.{ DisjunctionMatchers, DisjunctionValues, ValidationMatchers }

abstract class TestBase
  extends WordSpec
  with Matchers
  with OptionValues
  with DisjunctionMatchers
  with ValidationMatchers
  with DisjunctionValues
  with AsyncAssertions
  with BeforeAndAfterAll
