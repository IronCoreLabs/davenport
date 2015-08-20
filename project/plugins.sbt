resolvers += Resolver.sonatypeRepo("releases")

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// Styling and static code checkers
addSbtPlugin("org.brianmckenna" % "sbt-wartremover" % "0.12")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")


// Per the readme, this is needed in 1.3 to work around an issue
resolvers += Resolver.url("scoverage-bintray", url("https://dl.bintray.com/sksamuel/sbt-plugins/"))(Resolver.ivyStylePatterns)

resolvers += Classpaths.sbtPluginReleases

// *** Publish to bintray maven repo
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

// *** Measure code coverage of unit tests
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.1")

// *** Documentation Stuff
resolvers += Resolver.url(
  "tpolecat-sbt-plugin-releases",
    url("http://dl.bintray.com/content/tpolecat/sbt-plugin-releases"))(
        Resolver.ivyStylePatterns)

addSbtPlugin("org.tpolecat" % "tut-plugin" % "0.4.0")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")
addSbtPlugin("com.typesafe.sbt"  % "sbt-site"    % "0.8.1")
addSbtPlugin("com.typesafe.sbt"  % "sbt-ghpages" % "0.5.3")
