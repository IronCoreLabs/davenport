//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz.NonEmptyList

/**
 * The configuration class to tell Davenport how to configure the [[CouchConnection]].
 * @param ioPoolSize - Number of threads to use for IO in the underlying couchbase connection.
 * @param computationPoolSize - Number of threads to use for computation in underlying couchbase connection.
 * @param kvEndpoints - Number of sockets to keep open per node. Should be left at 1 unless you're sure the socket is saturated.
 * @param hosts - ip addresses or hostnames of the couchbase cluster nodes.
 */
final case class DavenportConfig(
  ioPoolSize: Int,
  computationPoolSize: Int,
  kvEndpoints: Int,
  hosts: NonEmptyList[String]
)

final object DavenportConfig {
  /**
   * Produce a DavenportConfig object with default configuration.
   */
  final def withDefaults(
    poolSize: Int = DefaultPoolSize,
    computationPoolSize: Int = DefaultComputationPoolSize,
    kvEndpoints: Int = DefaultKeyValueEndpoints,
    hosts: NonEmptyList[String] = NonEmptyList.nels("127.0.0.1")
  ): DavenportConfig = DavenportConfig(poolSize, computationPoolSize, kvEndpoints, hosts)
  private final val DefaultPoolSize: Int = 4
  private final val DefaultComputationPoolSize: Int = 4
  private final val DefaultKeyValueEndpoints: Int = 1
}
