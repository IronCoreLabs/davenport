//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package util

import observable.{ toSingleItemTask, toListTask }
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

  "observable.toListTask" should {
    val error = new Exception("error")
    val value1 = 1
    val value2 = 2
    "return failed task for error" in {
      toListTask(Observable.error(error)).attemptRun.leftValue shouldBe error
    }
    "return a Nil for empty observable" in {
      toListTask(Observable.empty).attemptRun.value shouldBe Nil
    }
    "return a List for a non empty observable" in {
      val l = List(value1, value2)
      toListTask(Observable.from(l)).attemptRun.value shouldBe l
    }
    "return an error for an observable with a good value and an error" in {
      toListTask(Observable.just(value1) ++ Observable.error(error)).attemptRun.leftValue shouldBe error
    }
  }
}
