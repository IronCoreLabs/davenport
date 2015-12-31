//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package codec

import scodec.bits.ByteVector
import scalaz._, Scalaz._
import argonaut._

//COLT: Correct error type
case class ByteVectorEncoder[A](encode: A => EncodeError \/ ByteVector) {
  def contramap[B](f: B => A): ByteVectorEncoder[B] = ByteVectorEncoder { b => encode(f(b)) }
}

object ByteVectorEncoder {
  implicit final val IdEncoder: ByteVectorEncoder[ByteVector] = ByteVectorEncoder { b => b.right }
  implicit final val StringEncoder: ByteVectorEncoder[String] = ByteVectorEncoder { string =>
    \/.fromEither(ByteVector.encodeUtf8(string)).leftMap { ex =>
      //COLT: It might not be wise to include this string as it might be many MB.
      EncodeError(s"Couldn't encode the string '$string'", Some(ex))
    }
  }
  implicit final val JsonEncoder: ByteVectorEncoder[Json] =
    ByteVectorEncoder { json => StringEncoder.encode(json.nospaces) }
  def fromDecodeJson[A](jsonEncode: EncodeJson[A]): ByteVectorEncoder[A] =
    ByteVectorEncoder { a => JsonEncoder.encode(jsonEncode.encode(a)) }
}
