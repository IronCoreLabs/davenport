//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz.NonEmptyList
/**
 * The configuration class to tell Davenport how to configure the [[CouchConnection]].
 * @param poolSize: Number of threads to use for IO.
 * @param
 */
final case class DavenportConfig(
  poolSize: Option[Int],
  computationPoolSize: Option[Int],
  kvEndpoints: Option[Int],
  hosts: NonEmptyList[String]
)

final object DavenportConfig {
  final def withDefaults = DavenportConfig(None, None, None, NonEmptyList.nels("127.0.0.1"))
}
