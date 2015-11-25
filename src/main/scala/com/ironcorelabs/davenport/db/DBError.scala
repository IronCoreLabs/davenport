//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport.db

/**
 * ADT for errors that might happen in working with our grammar.
 */
sealed abstract class DBError {
  def message: String
}
/**
 * If no value was found at the requested key.
 */
final case class ValueNotFound(key: Key) extends DBError {
  def message: String = s"No value found for key '$key'."
}
/**
 * If a value already exists at key.
 */
final case class ValueExists(key: Key) extends DBError {
  def message: String = s"Value for '$key' already exists."
}
/**
 * If the CommitVersion for an update doesn't match.
 */
final case class CommitVersionMismatch(key: Key) extends DBError {
  def message: String = s"The CommitVersion for '$key' was incorrect."
}

/**
 * Deserialization failures will be wrapped up in this type.
 * @param key The key that was requested
 * @param errorMessage The underlying failure message from the parse failure.
 * @param value Value that failed to parse.
 */
final case class DeserializationError(key: Key, value: String, errorMessage: String) extends DBError {
  def message: String = s"Failed to deserialize '$value' which was at '$key' with message '$errorMessage'"
}

/**
 * All other errors will be exceptions that come out of the underlying store. They'll be
 * wrapped up in this type.
 */
final case class GeneralError(ex: Throwable) extends DBError {
  def message: String = ex.getMessage
}
