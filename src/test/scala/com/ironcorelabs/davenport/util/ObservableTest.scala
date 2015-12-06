//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package util

import observable.toSingleItemTask
import rx.lang.scala.Observable

class ObservableTest extends TestBase {
  "observable.toSingleItemTask" should {
    val error = new Exception("error")
    val value1 = 1
    val value2 = 2
    "return failed task for error" in {
      toSingleItemTask(Observable.error(error)).attemptRun.leftValue shouldBe error
    }
    "return a None for empty observable" in {
      toSingleItemTask(Observable.empty).attemptRun.value shouldBe None
    }
    "return a Some for a non empty observable" in {
      toSingleItemTask(Observable.from(List(value1, value2))).attemptRun.value shouldBe Some(value1)
    }
    "return a Some for an observable with a good value and an error" in {
      toSingleItemTask(Observable.just(value1) ++ Observable.error(error)).attemptRun.value shouldBe Some(value1)
    }
  }
}
