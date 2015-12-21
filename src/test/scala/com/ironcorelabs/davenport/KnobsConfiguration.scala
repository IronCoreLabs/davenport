//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import knobs.{ Required, Optional, FileResource, Config, ClassPathResource }
import java.io.File

import scalaz.concurrent.Task

/**
 * Mix this in if you need configuration from knobs for your test.
 */
trait KnobsConfiguration {
  private final def configFileName = "davenport-test.cfg"
  private lazy val config: Task[Config] = knobs.loadImmutable(List(Required(ClassPathResource(configFileName))))
  protected lazy val knobsConfiguration: Task[DavenportConfig] = config.map { cfg =>
    DavenportConfig.withDefaults().
      setIOPoolSize(cfg.lookup[Int]("cdb.ioPoolSize")).
      setComputationPoolSize(cfg.lookup[Int]("cdb.computationPoolSize")).
      setHosts(cfg.lookup[String]("cdb.host").toList) //Only support 1 value for host
  }
}
