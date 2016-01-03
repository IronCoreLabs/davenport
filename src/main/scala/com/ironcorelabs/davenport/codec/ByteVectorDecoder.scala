//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package codec

import scodec.bits.ByteVector
import scalaz._, Scalaz._
import argonaut._

/**
 * Decode a ByteVector into some type A
 */
case class ByteVectorDecoder[A](decode: ByteVector => DecodeError \/ A) {
  /**
   * Alias for [[ByteVectorDecoder.decode]].
   */
  def apply(b: ByteVector): DecodeError \/ A = decode(b)
  def map[B](f: A => B): ByteVectorDecoder[B] = ByteVectorDecoder(decode.map(_.map(f)))
}

object ByteVectorDecoder {
  implicit final val FunctorInstance: Functor[ByteVectorDecoder] = new Functor[ByteVectorDecoder] {
    def map[A, B](decoder: ByteVectorDecoder[A])(f: A => B) = decoder.map(f)
  }
  implicit final val IdDecoder: ByteVectorDecoder[ByteVector] = ByteVectorDecoder { b => b.right }
  implicit final val StringDecoder: ByteVectorDecoder[String] = ByteVectorDecoder { b =>
    \/.fromEither(b.decodeUtf8).leftMap(ex => DecodeError("Couldn't decode the bytes into a utf8 string", Some(ex)))
  }
  final def fromDecodeJson[A](d: DecodeJson[A]): ByteVectorDecoder[A] = ByteVectorDecoder { bytes =>
    for {
      string <- StringDecoder(bytes)
      json <- JsonParser.parse(string).leftMap(message => DecodeError(s"Json parse failed with '$message'"))
      a <- d.decodeJson(json).toDisjunction.leftMap {
        case (message, history) =>
          new DecodeError(s"Failed to decode json giving excuse: '$message' at '$history'")
      }
    } yield a
  }
}

/**
 * Error indicating a failure to decode a value from a ByteVector.
 */
case class DecodeError(message: String, cause: Option[Exception] = None)
