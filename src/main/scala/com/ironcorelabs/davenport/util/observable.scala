//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package util

import rx.lang.scala.Observable
import scalaz.concurrent.Task
import scalaz.syntax.either._

object observable { //scalastyle:ignore
  /**
   * Get the first value of the observable in a Task. If there was an error fail the task using that exception.
   */
  def toSingleItemTask[A](o: Observable[A]): Task[Option[A]] = Task.async[Option[A]](f => {
    o.headOption.subscribe(
      n => f(n.right),
      e => f(e.left),
      () => ()
    )
    ()
  })
}
