package sbtcapsule

import sbt.{Defaults, _}
import Keys._
import sbt.plugins.{IvyPlugin, JvmPlugin}

object SbtCapsule extends AutoPlugin with CapsuleOps {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = IvyPlugin && JvmPlugin

  trait Keys {
    val Capsule = config("capsule")

    val capsulePackageOptions = taskKey[CapsulePackageOptions]("Package Options for capsule")
  }

  object autoImport extends Keys {

  }

  import autoImport._

  override def projectSettings = Seq(
    ivyConfigurations += Capsule,
    version in Capsule := "1.0.2",
    libraryDependencies <+= (version in Capsule).apply(v => "co.paralleluniverse" % "capsule" % v % "capsule->default"),
    capsulePackageOptions <<= (fullClasspath in Runtime, target, name, version in ThisBuild).map((runClassPath, trgt, nm, v) => {
      val options = CapsulePackageOptions(CapsuleConfig("meh"), trgt, nm, v, runClassPath)
      println(options)
      options
    }),
    managedClasspath in Capsule <<= (classpathTypes in Capsule, update) map { (ct, report) =>
      Classpaths.managedJars(Capsule, ct, report)
    },
    packageBin in Capsule <<= (capsulePackageOptions, managedClasspath in Capsule, streams).map( (opts, capsuleCP, streams) => doPackage(opts, capsuleCP, streams.log)),
    Keys.`package` in Capsule <<= packageBin in Capsule
  )
}

trait CapsuleOps {
  private def findCapsulejar(classpath: Classpath) = {
    classpath.files.filter(_.name.endsWith("jar")).find(_.name.contains("capsule"))
  }

  def doPackage(packageOptions: CapsulePackageOptions, capsuleClassPath: Classpath, log: Logger): File = {
    val capsuleJar = findCapsulejar(capsuleClassPath)
    log.info("Found capsulejar: " + capsuleJar)
    //IO.jar()
    packageOptions.target / (name + ".jar")
  }
}


sealed trait Application

case class MainClassApplication(mainClass: String) extends Application
case class ScriptApplication(script: String) extends Application
case class MavenApplication(moduleID: ModuleID) extends Application


case class CapsulePackageOptions(
                                capsuleConfig: CapsuleConfig,
                                target: File,
                                name: String,
                                version: String,
                                mainClassPath: Classpath
                                )

case class CapsuleConfig(
  mainClass: String,
  extract: Boolean = false,
  vmArgs: List[String] = Nil,
  systemProperties: Map[String, String] = Map.empty
)
