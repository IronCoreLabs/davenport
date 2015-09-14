---
title: Getting Started
layout: tutorial
---

## sbt

First, in your `build.sbt` file, you will want to reference davenport like so:

    libraryDependencies ++= Seq(
        "default" %% "davenport" % "0.5.21",
        "org.scalaz" %% "scalaz-core" % "7.1.2", // for type awesomeness
        "org.scalaz" %% "scalaz-concurrent" % "7.1.2", // for type awesomeness
        "io.argonaut" %% "argonaut" % "6.1" // json (de)serialization scalaz style
    )

**Note:** see the [github page](https://github.com/ironcorelabs/davenport) for the latest stable version number to use.

## Setup Couchbase

If you already have Couchbase installed in some way you're happy with, skip to the next section.  If not, then [install Docker](http://www.lmgtfy.com/?q=install+docker&l=1) and then follow these steps for a quick setup:

    > docker pull zmre/couchbase-enterprise-ubuntu:4.0.0-beta
    > docker run --name couchbase -p 8091:8091 -p 8092:8092 -p 8093:8093 -p 11207:11207 -p 11210:11210 -p 11211:11211 -p 18091:18091 -p 18092:18092 -h couchbase.local -d zmre/couchbase-enterprise-ubuntu:4.0.0-beta
    > docker exec -it couchbase /usr/local/bin/couchbase-setup.sh

## Configuration

We only support a few configuration parameters at the moment.  Please do submit pull requests for enhancements to `CouchConnection.scala` to add more powerful configuration options.  Within your repo, you can add a `couchbase.cfg` file into your classpath.  We recommend using the `./src/main/resources` path if you're unsure.  This config file will be checked in and used by all developers of your repo.  If developers want to override the default configuration stored in the repo, they should add `couchbase-dev.cfg` to their `.gitignore` file and add a `couchbase-dev.cfg` file to the root of the project.  Any values in that dev file will overwrite the values in the config file in the classpath.  Here is an example config file:

    cdb {
      host = "couchbase.local"
      bucketName = "default"
      queryEnabled = false
      ioPoolSize = 4
      computationPoolSize = 4
      kvEndpoints = 2
    }


