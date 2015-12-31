//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package codec

/**
 * Error ADT for encoding/decoding bytevectors. See [[ByteVectorDecoder]] and [[ByteVectorEncoder]]
 */
sealed trait CodecError

/**
 * Error indicating a failure to encode a value to ByteVector.
 */
case class EncodeError(message: String, cause: Option[Exception] = None) extends CodecError

/**
 * Error indicating a failure to decode a value from a ByteVector.
 */
case class DecodeError(message: String, cause: Option[Exception] = None) extends CodecError
