//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package codec

import scodec.bits.ByteVector
import scalaz._, Scalaz._
import argonaut._

//COLT: Correct error type
case class ByteVectorDecoder[A](decode: ByteVector => DecodeError \/ A) {
  def map[B](f: A => B): ByteVectorDecoder[B] = ByteVectorDecoder(decode.map(_.map(f)))
}

object ByteVectorDecoder {
  implicit final val IdDecoder: ByteVectorDecoder[ByteVector] = ByteVectorDecoder { b => b.right }
  implicit final val StringDecoder: ByteVectorDecoder[String] = ByteVectorDecoder(decodeIntoString(_))
  def fromDecodeJson[A](d: DecodeJson[A]): ByteVectorDecoder[A] = ByteVectorDecoder { bytes =>
    for {
      string <- decodeIntoString(bytes)
      json <- JsonParser.parse(string).leftMap(message => DecodeError(s"Json parse failed with '$message'"))
      a <- d.decodeJson(json).toDisjunction.leftMap {
        case (message, history) =>
          new DecodeError(s"Failed to decode json giving excuse: '$message' at '$history'")
      }
    } yield a
  }

  private final def decodeIntoString(b: ByteVector): DecodeError \/ String =
    \/.fromEither(b.decodeUtf8).leftMap(ex => DecodeError("Couldn't decode the bytes into a utf8 string", Some(ex)))
}
