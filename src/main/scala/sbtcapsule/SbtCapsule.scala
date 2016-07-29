package sbtcapsule

import java.nio.file.Files

import sbt._
import Keys._
import sbt.plugins.{IvyPlugin, JvmPlugin}
import java.util.jar.Manifest


object SbtCapsule extends AutoPlugin with CapsuleOps {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = IvyPlugin && JvmPlugin

  trait Keys {
    val Capsule = config("capsule")

    val capsulePackageOptions = taskKey[CapsulePackageOptions]("Package Options for capsule")
    val capsuleConfig = taskKey[CapsuleConfig]("Configuration for the packaged Capsule")
    val capsuleJarFile = settingKey[File]("Resulting path of jarfile")
  }

  object autoImport extends Keys

  import autoImport._

  override def projectSettings = Seq(
    ivyConfigurations += Capsule,
    version in Capsule := "1.0.2",
    libraryDependencies <+= (version in Capsule).apply(v => "co.paralleluniverse" % "capsule" % v % "capsule->default"),
    capsuleJarFile <<= (crossTarget, crossPaths, scalaBinaryVersion, name, version in ThisBuild).apply((crossTarget, crossPaths, binaryVersion, name, version) => {
      val jarName = (if (crossPaths) s"${name}_${binaryVersion}" else name) + s"-capsule-$version.jar"
      crossTarget / jarName
    }),
    capsuleConfig <<= (mainClass in Compile).map((main) => {
      CapsuleConfig(MainClassApplication(main.getOrElse(sys.error("No main class detected, you need to add one or configure the capsule"))))
    }),
    capsulePackageOptions <<= (capsuleConfig, fullClasspath in Runtime, crossTarget, capsuleJarFile, name, version in ThisBuild).map((config, runClassPath, trgt, capsuleJar, name, v) => {
      val options = CapsulePackageOptions(config, trgt, capsuleJar, name, v, runClassPath)
      println(options)
      options
    }),
    managedClasspath in Capsule <<= (classpathTypes in Capsule, update) map { (ct, report) =>
      Classpaths.managedJars(Capsule, ct, report)
    },
    packageBin in Capsule <<= (packageBin in Compile, capsulePackageOptions, managedClasspath in Capsule, streams).map{ (mainJar, opts, capsuleCP, streams) =>
      doPackage(opts, capsuleCP, mainJar)(streams.log)
    },
    Keys.`package` in Capsule <<= packageBin in Capsule
  )
}

object CapsuleAttributes {
  import java.util.jar.Attributes.Name
  val ApplicationID = new Name("Application-ID")
  val ApplicationVersion = new Name("Application-Version")
  val ApplicationClass = new Name("Application-Class")
  val ApplicationScript = new Name("Application-Script")
  val Application = new Name("Application")
  val MinJavaVersion = new Name("Min-Java-Version")
  val JVMArgs = new Name("JVM-Args")
  val SystemProperties = new Name("System-Properties")
  val JavaAgents = new Name("Java-Agents")
  val PreMainClass = new Name("Premain-Class")
  val Extract = new Name("Extract")
}

trait CapsuleOps {
  private def findCapsulejar(classpath: Classpath) = {
    classpath.files.filter(_.name.endsWith("jar")).find(_.name.contains("capsule"))
  }

  private def extractCapsule(target: File, classpath: Classpath)(implicit log: Logger) = {
    val file = target / "capsule-classes" / "Capsule.class"
    if (!file.exists()) {
      if (!file.getParentFile.exists() && !file.getParentFile.mkdirs()) {
        log.warn("Unable to create " + file.getAbsolutePath)
      }

      val capsuleJar = findCapsulejar(classpath)
      capsuleJar.foreach { jar =>

        Using.jarFile(true).apply(jar) { jf =>
          val entryUser = Using.zipEntry(jf)
          Option(jf.getEntry("Capsule.class")).foreach { e =>
            entryUser.apply(e)(is => Files.copy(is, file.toPath))
          }
        }
      }
    }
    file
  }

  private def buildManifest(config: CapsuleConfig): Manifest = {
    import java.util.jar.Attributes
    import collection.JavaConversions._

    val applicationTuple = config.application match {
      case MainClassApplication(mc) => CapsuleAttributes.ApplicationClass -> mc
      case ScriptApplication(script) => CapsuleAttributes.ApplicationScript -> script
      case ExecutableJarApplication(path) => CapsuleAttributes.Application -> path
      case MavenApplication(moduleId) => CapsuleAttributes.Application -> {
        List(moduleId.organization, moduleId.name, moduleId.revision).mkString(":")
      }
    }

    val attributes = Package.ManifestAttributes(
      Attributes.Name.MAIN_CLASS -> "Capsule",
      CapsuleAttributes.PreMainClass -> "Capsule",
      CapsuleAttributes.Extract -> config.extract.toString
    )

    val manifest = new Manifest
    val manifestAttributes = manifest.getMainAttributes

    manifestAttributes ++= attributes.attributes
    manifestAttributes += applicationTuple

    if (config.vmArgs.nonEmpty) {
      manifestAttributes += CapsuleAttributes.JVMArgs -> config.vmArgs.mkString(" ")
    }

    if (config.systemProperties.nonEmpty) {
      manifestAttributes += CapsuleAttributes.SystemProperties -> config.systemProperties.map { case (k, v) => s"$k=$v" }.mkString(" ")
    }
    config.minJavaVersion.foreach{ v =>
      manifestAttributes += CapsuleAttributes.MinJavaVersion -> v
    }

    manifest
  }

  def doPackage(packageOptions: CapsulePackageOptions, capsuleClassPath: Classpath, mainJar: File)(implicit log: Logger): File = {
    val jarFile = packageOptions.jarFile
    val capsuleClass = extractCapsule(packageOptions.target, capsuleClassPath)

    val classpathEntries = List(mainJar -> mainJar.getName) ++ packageOptions.mainClassPath.files.filter(_.getName.endsWith(".jar")).map(f => f -> f.getName)

    val sources = Map(
      capsuleClass -> capsuleClass.getName
    ).toList ++ classpathEntries

    val manifest = buildManifest(packageOptions.capsuleConfig)

    Package.makeJar(sources, jarFile, manifest, log)

    jarFile
  }
}


sealed trait Application

case class MainClassApplication(mainClass: String) extends Application
case class ScriptApplication(script: String) extends Application
case class MavenApplication(moduleID: ModuleID) extends Application
case class ExecutableJarApplication(path: String) extends Application


case class CapsulePackageOptions(
                                capsuleConfig: CapsuleConfig,
                                target: File,
                                jarFile: File,
                                name: String,
                                version: String,
                                mainClassPath: Classpath
                                )

case class CapsuleConfig(
  application: Application,
  extract: Boolean = true,
  vmArgs: List[String] = Nil,
  systemProperties: Map[String, String] = Map.empty,
  minJavaVersion: Option[String] = None
)
