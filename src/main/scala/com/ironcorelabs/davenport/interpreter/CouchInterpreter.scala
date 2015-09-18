//
// com.ironcorelabs.davenport.CouchInterpreter
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package interpreter

import scalaz.{ \/, \/-, ~>, Kleisli, Free }
import scalaz.concurrent.Task
import scalaz.std.anyVal._
import scalaz.syntax.either._
import DB._
import scalaz.stream.Process

// Couchbase
import com.couchbase.client.java.{ ReplicateTo, PersistTo, ReplicaMode, CouchbaseCluster, Bucket, AsyncBucket }
import com.couchbase.client.java.env.{ CouchbaseEnvironment, DefaultCouchbaseEnvironment }
import com.couchbase.client.java.document._
import com.couchbase.client.java.error._

// RxScala (Observables) used in Couchbase client lib async calls
import rx.lang.scala.Observable
import rx.lang.scala.JavaConversions._
import com.ironcorelabs.davenport.syntax.dbprog._

abstract class CouchInterpreter extends Interpreter {
  import CouchInterpreter._
  def bucket: Task[Bucket]
  /**
   * Given a Bucket return back a NT that can turn DBOps into a Task.
   */
  def interpret: (DBOps ~> Task) = new (DBOps ~> Task) {
    def apply[A](prog: DBOps[A]): Task[A] =
      bucket.flatMap(interpretK(prog).run(_))
  }
}

/**
 * Things related to translating DBOp to Kleisli[Task, Bucket, A] and some helpers for translating to Task[A]
 */
final object CouchInterpreter {
  def apply(b: Task[Bucket]): CouchInterpreter = new CouchInterpreter {
    val bucket = b
  }

  type CouchK[A] = Kleisli[Task, Bucket, A]

  /**
   * Interpret the program into a Kleisli that will take a Bucket as its argument. Useful if you want to do
   * Kleisli arrow composition before running it.
   */
  def interpretK[A](prog: DBProg[A]): CouchK[Throwable \/ A] = interpretK(prog.run)

  /**
   * Basic building block. Turns the DbOps into a Kleisli which takes a Bucket, used by interpret above.
   */
  def interpretK[A](prog: DBOps[A]): CouchK[A] = Free.runFC[DBOp, CouchK, A](prog)(couchRunner)

  /**
   * We use co-yoneda to run our `scalaz.Free`.
   *
   * In this case, the couchRunner object transforms [[DB.DBOp]] to
   * `scalaz.concurrent.Task`.
   * The only public method, apply, is what gets called as the grammar
   * is executed, calling it to transform [[DB.DBOps]] to functions.
   */
  private val couchRunner = new (DBOp ~> CouchK) {
    def apply[A](dbp: DBOp[A]): Kleisli[Task, Bucket, A] = dbp match {
      case GetDoc(k: Key) => getDoc(k)
      case CreateDoc(k: Key, v: RawJsonString) => createDoc(k, v)
      case GetCounter(k: Key) => getCounter(k)
      case IncrementCounter(k: Key, delta: Long) => incrementCounter(k, delta)
      case RemoveKey(k: Key) => removeKey(k)
      case UpdateDoc(k: Key, v: RawJsonString, h: HashVer) => updateDoc(k, v, h)
    }

    /*
     * Helpers for the grammar interpreter
     */
    private def getDoc(k: Key): CouchK[Throwable \/ DbValue] =
      couchOpToDBValue(_.get(k.value, classOf[RawJsonDocument]))

    private def createDoc(k: Key, v: RawJsonString): CouchK[Throwable \/ DbValue] =
      couchOpToDBValue(_.insert(
        RawJsonDocument.create(k.value, 0, v.value, 0)
      ))

    private def getCounter(k: Key): CouchK[Throwable \/ Long] =
      couchOpToLong(_.counter(k.value, 0, 0, 0))

    private def incrementCounter(k: Key, delta: Long): CouchK[Throwable \/ Long] =
      couchOpToLong(
        // here we use delta as the default, so if you want an increment
        // by one on a key that doesn't exist, we'll give you a 1 back
        // and if you want an increment by 10 on a key that doesn't exist,
        // we'll give you a 10 back
        _.counter(k.value, delta, delta, 0)
      )

    private def removeKey(k: Key): CouchK[Throwable \/ Unit] =
      couchOpToA[Unit, String](
        _.remove(k.value, classOf[RawJsonDocument])
      )(_ => ().right)

    private def updateDoc(k: Key, v: RawJsonString, h: HashVer): CouchK[Throwable \/ DbValue] =
      couchOpToA(_.replace(
        RawJsonDocument.create(k.value, 0, v.value, h.value)
      ))(doc => DbValue(RawJsonString(doc.content), HashVer(doc.cas)).right)

    private def couchOpToLong(fetchOp: AsyncBucket => Observable[JsonLongDocument]): CouchK[Throwable \/ Long] = {
      couchOpToA(fetchOp)(doc => \/-(doc.content))
    }

    private def couchOpToDBValue(fetchOp: AsyncBucket => Observable[RawJsonDocument]): CouchK[Throwable \/ DbValue] =
      couchOpToA(fetchOp)(doc => DbValue(RawJsonString(doc.content), HashVer(doc.cas)).right)

    private def couchOpToA[A, B](fetchOp: AsyncBucket => Observable[AbstractDocument[B]])(f: AbstractDocument[B] => Throwable \/ A): Kleisli[Task, Bucket, Throwable \/ A] = Kleisli.kleisli { bucket =>
      obs2Task(fetchOp(bucket.async)).map(_.flatMap(f))
    }

    // This is the most efficient way of running things in the couchbase lib
    // -- far more efficient then using the blocking observables. This converts
    // the callbacks from the observable into a task, which is easier to work
    // with when composing and reasoning about functions and operations.
    private def obs2Task[A](o: Observable[A]): Task[Throwable \/ A] = {
      Task.async[A](k => {
        o.headOption.subscribe(
          n => k(n.map(_.right).getOrElse(new DocumentDoesNotExistException().left)),
          e => k(e.left),
          () => ()
        )
        ()
      }).attempt
    }
  }
}
