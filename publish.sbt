publishMavenStyle := true

pomIncludeRepository := { _ => false }

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
