//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport.db

/** Just a string. This is used for type safety. */
final case class Key(value: String) extends AnyVal

/** Just a string. This is used for type safety. */
final case class RawJsonString(value: String) extends AnyVal

/**
 * A commit version of an existing value in the db.
 *
 *  Couchbase calls this a CAS (check and save) as it is passed back
 *  in with requests to update a value. If the value has been changed
 *  by another actor, then the update fails and the caller is left
 *  to handle the conflict.
 */
final case class CommitVersion(value: Long) extends AnyVal
