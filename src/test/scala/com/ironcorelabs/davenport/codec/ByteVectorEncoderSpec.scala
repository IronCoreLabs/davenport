//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package codec

import argonaut._
import scodec.bits.ByteVector
import org.scalacheck.Arbitrary
import Arbitrary.arbitrary
import scalaz._, Scalaz._

class ByteVectorEncoderSpec extends TestBase {
  val jsonValue = "\u03BB4life"
  val stringWithUnicode = s"""{"key":"$jsonValue"}"""
  val utf8Bytes = ByteVector.encodeUtf8(stringWithUnicode).right.get
  val utf16Bytes = ByteVector.view(stringWithUnicode.getBytes("UTF16"))

  implicit val arbByteVector = Arbitrary {
    arbitrary[String].map(s => ByteVector.view(s.getBytes("UTF8")))
  }
  implicit def arbByteEncoder[A: Arbitrary]: Arbitrary[ByteVectorEncoder[A]] = Arbitrary {
    arbitrary[A => ByteVector].map(ByteVectorEncoder(_))
  }

  "ByteVectorEncoder" should {
    "encode bytes as identity" in { ByteVectorEncoder.IdEncoder(utf16Bytes) shouldBe utf16Bytes }
    "encode string as utf8" in { ByteVectorEncoder.StringEncoder(stringWithUnicode) shouldBe utf8Bytes }
    "encode json utf8Bytes" in { ByteVectorEncoder.fromEncodeJson(EncodeJson.of[Map[String, String]])(Map("key" -> jsonValue)) shouldBe utf8Bytes }

    "have lawful scalaz typeclasses" in {
      implicit val byteVectorEquals: Equal[ByteVector] = Equal.equalBy[ByteVector, List[Byte]](_.toArray.toList)
      //Function equality isn't possible so I chose to evaluate it at a single point and prove that the functor laws hold given that point.
      implicit def equal = Equal.equalBy[ByteVectorEncoder[Int], ByteVector](_.apply(0))
      import scalaz.scalacheck.ScalazProperties
      check(ScalazProperties.contravariant.laws[ByteVectorEncoder])
    }
  }
}
