name := "demo1"

scalaVersion := "2.10.6"

capsuleJarFile := crossTarget.value / "demo1-capsule.jar"

TaskKey[Unit]("check") <<= (crossTarget, version) map { (crossTarget, version) =>
  val process = sbt.Process("java", Seq("-jar", (crossTarget / "demo1-capsule.jar").toString))
  val out = (process!!)
  if (out.trim != "Hello World") sys.error("unexpected output: " + out)
  ()
}
