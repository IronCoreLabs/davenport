//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package codec

import scodec.bits.ByteVector
import scalaz._, Scalaz._
import argonaut._

case class ByteVectorEncoder[A](encode: A => EncodeError \/ ByteVector) {
  def apply(a: A): EncodeError \/ ByteVector = encode(a)
  def contramap[B](f: B => A): ByteVectorEncoder[B] = ByteVectorEncoder { b => encode(f(b)) }
}

object ByteVectorEncoder {
  implicit val ContravariantInstance: Contravariant[ByteVectorEncoder] = new Contravariant[ByteVectorEncoder] {
    def contramap[A, B](encoder: ByteVectorEncoder[A])(f: B => A) = encoder.contramap(f)
  }
  implicit final val IdEncoder: ByteVectorEncoder[ByteVector] = ByteVectorEncoder { b => b.right }
  implicit final val StringEncoder: ByteVectorEncoder[String] = ByteVectorEncoder { string =>
    \/.fromEither(ByteVector.encodeUtf8(string)).leftMap { ex =>
      //COLT: It might not be wise to include this string as it might be many MB.
      EncodeError(s"Couldn't encode the string '$string'", Some(ex))
    }
  }
  implicit final val JsonEncoder: ByteVectorEncoder[Json] = encodeBy(_.nospaces)
  def fromEncodeJson[A](jencode: EncodeJson[A]): ByteVectorEncoder[A] = encodeBy(jencode.encode(_))
  //Alias for contramap with an implicit encoder.
  def encodeBy[A, B](f: B => A)(implicit encoder: ByteVectorEncoder[A]): ByteVectorEncoder[B] = encoder.contramap(f)
}
