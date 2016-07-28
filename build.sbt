
organization := "net.hamnaberg.sbt"
name := "sbt-capsule"

sbtPlugin := true

//libraryDependencies += "co.paralleluniverse" % "capsule" % "1.0.2"

scriptedSettings

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + (version in ThisBuild).value)
}

scriptedBufferLog := false
