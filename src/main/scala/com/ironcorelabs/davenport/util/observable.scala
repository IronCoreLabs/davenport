//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package util

import rx.lang.scala.{ Observable, Observer }
import scalaz.concurrent.Task
import scalaz.syntax.either._
import scalaz.\/

final object observable { //scalastyle:ignore

  case class NoElementInObservableException() extends Exception("No elements in the observable.")
  /**
   * Get the first value of the observable in a Task. If there was an error fail the task.
   */
  final def toSingleItemTask[A](o: Observable[A]): Task[A] = toOptionTask(o).flatMap {
    case None => Task.fail(NoElementInObservableException())
    case Some(a) => Task.now(a)
  }

  /**
   * Get the first value of the observable in the Task, if the observable completes with no items return None.
   */
  final def toOptionTask[A](o: Observable[A]): Task[Option[A]] = Task.async(subscribe(o.headOption)(_))

  /**
   * Get all the values from the observable in a Task[List[A]].
   */
  final def toListTask[A](o: Observable[A]): Task[List[A]] = Task.async(subscribe(o.toList)(_))

  private final def subscribe[A](o: Observable[A])(f: (Throwable \/ A) => Unit): Unit = {
    o.subscribe(funcToObserver(f))
    ()
  }

  private final def funcToObserver[A](f: (Throwable \/ A) => Unit): Observer[A] = Observer[A](
    onNext = { a: A => f(a.right) },
    onError = { t: Throwable => f(t.left) }
  )
}
