//
// com.ironcorelabs.davenport.TestBase
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import org.scalatest.{ WordSpec, Matchers, OptionValues, BeforeAndAfterAll }
import org.scalatest.concurrent.AsyncAssertions
import org.typelevel.scalatest.{ DisjunctionValues, DisjunctionMatchers }

trait TestBase extends WordSpec with Matchers with DisjunctionValues with OptionValues with DisjunctionMatchers with AsyncAssertions with BeforeAndAfterAll
