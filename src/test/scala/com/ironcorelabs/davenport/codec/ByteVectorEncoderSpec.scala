//
// com.ironcorelabs.davenport.CouchDatastoreSpec
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
    arbitrary[A => ByteVector].map(f => ByteVectorEncoder(f.map(_.right)))
  }

  "ByteVectorEncoder" should {
    "encode bytes as identity" in { ByteVectorEncoder.IdEncoder(utf16Bytes).value shouldBe utf16Bytes }
    "encode string as utf8" in { ByteVectorEncoder.StringEncoder(stringWithUnicode).value shouldBe utf8Bytes }
    "encode json utf8Bytes" in {
      val result = ByteVectorEncoder.fromEncodeJson(EncodeJson.of[Map[String, String]])(Map("key" -> jsonValue)).value
      result shouldBe utf8Bytes
    }
    // "fail for invalid utf8 strings" in {
    //   ByteVector.fromHex("0xF4").value.encodeUtf8
    // }

    "have a lawful scalaz typeclasses" in {
      implicit val byteVectorEquals: Equal[ByteVector] = Equal.equalBy[ByteVector, List[Byte]](_.toArray.toList)
      //Function equality isn't possible so I chose to evaluate it at a single point and prove that the functor laws hold given that point.
      implicit def equal = Equal.equalBy[ByteVectorEncoder[Int], ByteVector](_.apply(0).getOrElse(throw new Exception("Encoding bytes failed")))
      import scalaz.scalacheck.ScalazProperties
      check(ScalazProperties.contravariant.laws[ByteVectorEncoder])
    }
  }
}
