name := "davenport"

organization := "com.ironcorelabs"

publishMavenStyle := true

publishArtifact in Test := false

// homepage := Some(url("https://ironcorelabs.com/davenport"))

pomIncludeRepository := { _ => false }

useGpg := true

// useGpgAgent := false

// PgpKeys.gpgCommand := "/usr/local/MacGPG2/bin/gpg2"

usePgpKeyHex("E84BBF42")

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
  <url>https://ironcorelabs.com/davenport</url>
    <licenses>
      <license>
        <name>MIT</name>
        <url>http://opensource.org/licenses/MIT</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:IronCoreLabs/davenport.git</url>
      <connection>scm:git:git@github.com:IronCoreLabs/davenport.git</connection>
    </scm>
    <developers>
      {
      Seq(
        ("zmre", "Patrick Walsh"),
        ("coltfred", "Colt Frederickson")
      ).map {
        case (id, name) =>
          <developer>
            <id>{id}</id>
            <name>{name}</name>
            <url>http://github.com/{id}</url>
          </developer>
      }
    }
    </developers>
  )
