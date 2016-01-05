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

class ByteVectorDecoderSpec extends TestBase {
  val jsonValue = "\u03BB4life"
  val stringWithUnicode = s"""{"key":"$jsonValue"}"""
  val utf8Bytes = ByteVector.encodeUtf8(stringWithUnicode).right.get
  val utf16Bytes = ByteVector.view(stringWithUnicode.getBytes("UTF16"))

  implicit def arbByteDecoder[A: Arbitrary] = Arbitrary {
    arbitrary[ByteVector => A].map(f => ByteVectorDecoder(f.map(_.right)))
  }

  "ByteVectorDecoder" should {

    "decode bytes as identity" in { ByteVectorDecoder.IdDecoder(utf16Bytes).value shouldBe utf16Bytes }
    "decode string as utf8" in { ByteVectorDecoder.StringDecoder(utf8Bytes).value shouldBe stringWithUnicode }
    "decode json utf8bytes" in {
      val result = ByteVectorDecoder.fromDecodeJson(DecodeJson.of[Map[String, String]])(utf8Bytes).value
      result.get("key").value shouldBe jsonValue
    }
    "fail decode json utf16Bytes" in {
      val failure = ByteVectorDecoder.fromDecodeJson(DecodeJson.of[Map[String, String]])(utf16Bytes).leftValue
      failure.cause.value shouldBe a[java.nio.charset.MalformedInputException]
      failure.message should include("Couldn't decode")
    }
    "fail decode invalid json bytes" in {
      val failure = ByteVectorDecoder.fromDecodeJson(DecodeJson.of[Map[String, String]])(utf8Bytes.drop(1)).leftValue
      failure.cause shouldBe None
      failure.message should include("Json parse failed with")
    }
    "fail decode valid json but invalid decoder" in {
      val failure = ByteVectorDecoder.fromDecodeJson(DecodeJson.of[Int])(utf8Bytes).leftValue
      failure.cause shouldBe None
      failure.message should include("Failed to decode json giving excuse")
    }

    "have lawful scalaz typeclasses" in {
      //Function equality isn't possible so I chose to evaluate it at a single point and prove that the functor laws hold given that point.
      implicit def equal[A: Equal] = Equal.equalBy[ByteVectorDecoder[A], A](_.apply(utf8Bytes).getOrElse(throw new Exception("Decoding bytes failed")))
      import scalaz.scalacheck.ScalazProperties
      check(ScalazProperties.functor.laws[ByteVectorDecoder])
    }
  }
}
