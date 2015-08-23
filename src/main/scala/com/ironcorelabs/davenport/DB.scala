//
// com.ironcorelabs.davenport.DB
//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task
import scala.language.implicitConversions
// to use any of this, import com.ironcorelabs.davenport.DB._

object DB {
  // 1. Basic building block types encoded as scala value classes (google that)
  final case class Key(value: String) extends AnyVal
  final case class RawJsonString(value: String) extends AnyVal
  final case class HashVer(value: Long) extends AnyVal
  final case class DbValue(jsonString: RawJsonString, hashVer: HashVer)
  final case class DbBatchError(recordNum: Int, errorString: String)

  // 2. Type aliases for simpler function signatures
  type DBOps[A] = Free.FreeC[DBOp, A]
  type DBProg[A] = EitherT[DBOps, Throwable, A]
  type DBBatchResults = (IList[(Int, Throwable)] \&/ IList[Int])
  type DBBatchStream = Iterator[Throwable \/ (DBProg[Key], RawJsonString)]

  // 3. Implicit lift required for Coyoneda shortcuts and other lift shortcuts
  implicit val MonadDBOps: Monad[DBOps] = Free.freeMonad[({ type l[a] = Coyoneda[DBOp, a] })#l]
  def liftIntoDBProg[A](opt: Option[A]): DBProg[A] = EitherT.eitherT(Monad[DBOps].point(opt \/> new Exception("Value not found")))
  def liftIntoDBProg[A](either: Throwable \/ A): DBProg[A] = EitherT.eitherT(Monad[DBOps].point(either))
  def liftIntoDBProg[A](opt: Option[A], errormessage: String): DBProg[A] = EitherT.eitherT(Monad[DBOps].point(opt \/> new Exception(errormessage)))
  def liftToFreeEitherT[A](a: DBOp[Throwable \/ A]): DBProg[A] = {
    val free: DBOps[Throwable \/ A] = Free.liftFC(a)
    EitherT.eitherT(free)
  }
  def dbProgFail[A](e: Throwable): DBProg[A] = liftIntoDBProg(e.left)
  def key2String(k: Key): String = k.value
  def string2Key(s: String): Key = Key(s)

  // 4. Algebraic Data Type of DB operations. (A persistence grammar of sorts.)
  sealed trait DBOp[+A]
  case class GetDoc(key: Key) extends DBOp[Throwable \/ DbValue]
  case class CreateDoc(key: Key, doc: RawJsonString) extends DBOp[Throwable \/ DbValue]
  case class UpdateDoc(key: Key, doc: RawJsonString, hashver: HashVer) extends DBOp[Throwable \/ DbValue]
  case class RemoveKey(key: Key) extends DBOp[Throwable \/ Unit]
  case class GetCounter(key: Key) extends DBOp[Throwable \/ Long]
  case class IncrementCounter(key: Key, delta: Long = 1) extends DBOp[Throwable \/ Long]
  case class BatchCreateDocs(st: DBBatchStream, continue: Throwable => Boolean) extends DBOp[Throwable \/ DBBatchResults]

  // 5. Convenience functions lifting DBOp to EitherT[Free, Throwable,DBOp]
  def getDoc(k: Key): DBProg[DbValue] = liftToFreeEitherT(GetDoc(k))
  def createDoc(k: Key, doc: RawJsonString): DBProg[DbValue] =
    liftToFreeEitherT(CreateDoc(k, doc))
  def updateDoc(k: Key, doc: RawJsonString, hashver: HashVer): DBProg[DbValue] =
    liftToFreeEitherT(UpdateDoc(k, doc, hashver))
  def removeKey(k: Key): DBProg[Unit] = liftToFreeEitherT(RemoveKey(k))
  def getCounter(k: Key): DBProg[Long] = liftToFreeEitherT(GetCounter(k))
  def incrementCounter(k: Key, delta: Long = 1): DBProg[Long] =
    liftToFreeEitherT(IncrementCounter(k, delta))
  def batchCreateDocs(st: DBBatchStream, continue: Throwable => Boolean = _ => true): DBProg[DBBatchResults] = liftToFreeEitherT(BatchCreateDocs(st, continue))
  def modifyDoc(k: Key, f: RawJsonString => RawJsonString): DBProg[DbValue] = for {
    t <- getDoc(k)
    res <- updateDoc(k, f(t.jsonString), t.hashVer)
  } yield res

  // Random other type conveniences
  def batchFailed(idx: Int, e: Throwable): DBBatchResults =
    IList((idx, e)).wrapThis[IList[Int]]
  def batchSucceeded(idx: Int): DBBatchResults =
    IList(idx).wrapThat[IList[(Int, Throwable)]]
}
