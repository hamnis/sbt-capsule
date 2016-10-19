name := "demo2"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "net.databinder" %% "unfiltered-jetty" % "0.8.4",
  "net.databinder" %% "unfiltered-filter" % "0.8.4"
)

capsuleJarFile := crossTarget.value / "demo2-capsule.jar"

capsuleConfig := capsuleConfig.value.copy(extract = false)

TaskKey[Unit]("check") <<= (crossTarget, version) map { (crossTarget, version) =>
  val process = sbt.Process("java", Seq("-jar", (crossTarget / "demo2-capsule.jar").toString))
  val out = (process!!)
  if (out.trim != "Hello World") sys.error("unexpected output: " + out)
  ()
}
