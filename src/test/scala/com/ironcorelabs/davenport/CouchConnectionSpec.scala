//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport

import scalaz._, Scalaz._, scalaz.concurrent.Task, scalaz.stream.Process
import db._
import tags.RequiresCouch
import syntax.dbprog._
import scala.concurrent.duration._

@RequiresCouch
class CouchConnectionSpec extends TestBase {

  "CouchConnection" should {
    "attempt connect to bad host and fail" in {
      // Store off good connection
      CouchConnection.fakeDisconnect

      // Attempt to connect with bogus config
      val res = CouchConnection.connectToHost("badhostnocookie.local")

      // Get error in return
      res should be(left)

      CouchConnection.connected should ===(false)

      CouchConnection.fakeDisconnectRevert
    }
  }
}
