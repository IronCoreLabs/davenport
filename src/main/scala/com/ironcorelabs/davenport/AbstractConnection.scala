//
// com.ironcorelabs.davenport.AbstractConnection
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz.\/, scalaz.concurrent.Task, scalaz.Scalaz._

import DB._

trait AbstractConnection {
  def connect: Throwable \/ Unit
  def disconnect(): Unit
  def connected: Boolean
  def exec[A](db: DBProg[A]): Throwable \/ A
  def execTask[A](db: DBProg[A]): Task[Throwable \/ A]
  def execAsync[A](db: DBProg[A], callback: (Throwable \/ A) => Unit): Unit =
    execTask(db).runAsync(r => callback(r.join))
}
