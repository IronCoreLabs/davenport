//
// com.ironcorelabs.davenport.AbstractConnection
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz.\/, scalaz.concurrent.Task, scalaz.Scalaz._

import DB._

/**
 * A connection to some data backend
 *
 * @constructor Should be implemented as a singleton with no constructor
 */
trait AbstractConnection {
  def connect: Throwable \/ Unit
  def disconnect(): Unit
  def connected: Boolean
  def exec[A](db: DBProg[A]): Throwable \/ A
  def execTask[A](db: DBProg[A]): Task[Throwable \/ A]
  /**
   * Runs the task with a callback function on completion
   */
  def execAsync[A](db: DBProg[A], callback: (Throwable \/ A) => Unit): Unit =
    execTask(db).runAsync(r => callback(r.join))
}
