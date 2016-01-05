//
// Copyright (c) 2015 IronCore Labs
//
package com.ironcorelabs.davenport
package internal

import com.couchbase.client.core.config.ConfigurationException
import com.couchbase.client.core.CouchbaseException
import error._
import scalaz.concurrent.Task

class CouchbaseCoreUtilTest extends TestBase {
  "bucketErrorHandler" should {
    import CouchbaseCoreUtil.bucketErrorHandler
    val errorHandler = bucketErrorHandler[Bucket]("name").lift

    def generateConfigurationError(message: String): Task[Bucket] = {
      val error = new ConfigurationException("", new IllegalStateException(message))
      errorHandler(error).value
    }

    "convert 'does not exist' failure" in { generateConfigurationError("NOT_EXISTS").attemptRun.leftValue shouldBe a[BucketDoesNotExistException] }
    "convert Unauthorized failure" in { generateConfigurationError("Unauthorized").attemptRun.leftValue shouldBe an[InvalidPasswordException] }
    "pass through other failure" in { generateConfigurationError("blargh").attemptRun.leftValue shouldBe a[ConfigurationException] }
    "pass through CouchbaseException" in { errorHandler(new CouchbaseException("ha!")).value.attemptRun.leftValue shouldBe a[CouchbaseException] }
  }
}
