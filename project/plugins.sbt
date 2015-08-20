resolvers += Resolver.sonatypeRepo("releases")

// Styling and static code checkers
addSbtPlugin("org.brianmckenna" % "sbt-wartremover" % "0.12")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

//scoverage needs this
resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.1")

