
publishTo <<= (version) apply {
  (v: String) => if (v.trim().endsWith("SNAPSHOT")) Some(Opts.resolver.sonatypeSnapshots) else Some(Opts.resolver.sonatypeStaging)
}

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

homepage := Some(new URL("http://github.com/hamnis/sbt-capsule"))

startYear := Some(2016)

licenses := Seq(("Apache 2", new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")))

pomExtra <<= (pomExtra, name, description) {(pom, name, desc) => pom ++ xml.Group(
  <scm>
    <url>http://github.com/hamnis/sbt-capsule</url>
    <connection>scm:git:git://github.com/hamnis/sbt-capsule.git</connection>
    <developerConnection>scm:git:git@github.com:hamnis/sbt-capsule.git</developerConnection>
  </scm>
  <developers>
    <developer>
      <id>hamnis</id>
      <name>Erlend Hamnaberg</name>
      <url>http://twitter.com/hamnis</url>
    </developer>
  </developers>
)}

useGpg := true

disablePlugins(AetherPlugin)

enablePlugins(SignedAetherPlugin)

overridePublishSignedSettings
