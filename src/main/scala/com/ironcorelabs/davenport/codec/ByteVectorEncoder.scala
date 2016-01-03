//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package codec

import scodec.bits.ByteVector
import scalaz.{ Contravariant, \/ }
import argonaut._

/**
 * Encode some A into a ByteVector for serialization using the encode function.
 */
case class ByteVectorEncoder[A](encode: A => ByteVector) {
  def apply(a: A): ByteVector = encode(a)
  def contramap[B](f: B => A): ByteVectorEncoder[B] = ByteVectorEncoder { b => encode(f(b)) }
}

object ByteVectorEncoder {
  implicit val ContravariantInstance: Contravariant[ByteVectorEncoder] = new Contravariant[ByteVectorEncoder] {
    def contramap[A, B](encoder: ByteVectorEncoder[A])(f: B => A) = encoder.contramap(f)
  }
  implicit final val IdEncoder: ByteVectorEncoder[ByteVector] = ByteVectorEncoder(identity)
  implicit final val StringEncoder: ByteVectorEncoder[String] = ByteVectorEncoder { string =>
    \/.fromEither(ByteVector.encodeUtf8(string)).getOrElse {
      throw new IllegalArgumentException(s"It shouldn't be possible, but $string couldn't be encoded as bytes.")
    }
  }
  implicit final val JsonEncoder: ByteVectorEncoder[Json] = encodeBy(_.nospaces)
  /**
   * Given a jencode for converting A => Json we then create a ByteVectorEncoder which converts that Json into a
   * ByteVector.
   */
  def fromEncodeJson[A](jencode: EncodeJson[A]): ByteVectorEncoder[A] = encodeBy(jencode.encode(_))
  //Alias for contramap with an implicit encoder.
  def encodeBy[A, B](f: B => A)(implicit encoder: ByteVectorEncoder[A]): ByteVectorEncoder[B] = encoder.contramap(f)
}
