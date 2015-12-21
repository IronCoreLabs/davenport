//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

class DavenportConfigSpec extends TestBase {
  val defaultConfig = DavenportConfig.withDefaults()
  "DavenportConfig" should {
    "not change host on Nil" in {
      defaultConfig.setHosts(Nil) shouldBe defaultConfig
    }
    "change hosts on NEL" in {
      val hosts = List("1")
      defaultConfig.setHosts(hosts).hosts.list shouldBe hosts
    }
    "change ioPoolSize" in {
      defaultConfig.setIOPoolSize(Some(1)).ioPoolSize shouldBe 1
    }
    "change computationPoolSize" in {
      defaultConfig.setComputationPoolSize(Some(100)).computationPoolSize shouldBe 100
    }
    "change key value endpoints" in {
      defaultConfig.setKVEndpoints(Some(200)).kvEndpoints shouldBe 200
    }
  }
}
