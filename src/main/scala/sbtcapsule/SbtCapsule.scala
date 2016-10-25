package sbtcapsule

import java.nio.file.Files
import java.util.jar.Attributes.Name
import java.util.jar.{Attributes, Manifest}

import sbt.Keys._
import sbt._
import sbt.plugins.{IvyPlugin, JvmPlugin}

object SbtCapsule extends AutoPlugin with CapsuleOps {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = IvyPlugin && JvmPlugin

  trait CapsuleKeys {
    val Capsule = config("capsule")

    val capsulePackageOptions = taskKey[CapsulePackageOptions]("Package Options for capsule")
    val capsuleConfig = taskKey[CapsuleConfig]("Configuration for the packaged Capsule")
    val capsuleJarFile = settingKey[File]("Resulting path of jarfile")
  }

  object autoImport extends CapsuleKeys

  import autoImport._

  override def projectSettings = Seq(
    ivyConfigurations += Capsule,
    version in Capsule := "1.0.2",
    libraryDependencies += "co.paralleluniverse" % "capsule" % (version in Capsule).value % Capsule.name,
    artifact in Capsule := artifact.value.copy(classifier = Some("capsule")),
    capsuleJarFile := {
      val artifactName = Artifact.artifactName(ScalaVersion(scalaVersion.value, scalaBinaryVersion.value), projectID.value, (artifact in Capsule).value)
      crossTarget.value / artifactName
    },
    capsuleConfig := CapsuleConfig(
      MainClassApplication((mainClass in Compile).value.getOrElse(sys.error("No main class detected, you need to add one or configure the capsule")))
    ),
    capsulePackageOptions := {
      CapsulePackageOptions(capsuleConfig.value, crossTarget.value, capsuleJarFile.value, name.value, (version in ThisBuild).value, (fullClasspath in Runtime).value)
    },
    managedClasspath in Capsule := Classpaths.managedJars(Capsule, (classpathTypes in Capsule).value, update.value),
    packageBin in Capsule := doPackage(capsulePackageOptions.value, (managedClasspath in Capsule).value, (packageBin in Compile).value)(streams.value.log),
    Keys.`package` in Capsule := (packageBin in Capsule).value
  )
}

trait CapsuleOps {
  def extractCapsules(target: File, classpath: Classpath)(implicit log: Logger): List[File] = {
    import collection.JavaConverters._

    val capsuleClasses = target / "capsule-classes"

    if (!capsuleClasses.exists() && !capsuleClasses.mkdirs()) {
      log.warn("Unable to create " + capsuleClasses.getAbsolutePath)
    }

    val classes = if (Files.list(capsuleClasses.toPath).findFirst().isPresent) {
      Files.list(capsuleClasses.toPath)
        .iterator()
        .asScala
        .filter(_.endsWith(".class"))
        .map(_.toFile)
        .toList
    } else {
      classpath.files.filter(_.getName.endsWith("jar")).flatMap{ jar =>
        Using.jarFile(true)(jar) { jf =>
          val entryUser = Using.zipEntry(jf)
          jf.entries().asScala.toList.filter(_.getName.endsWith(".class")).map{entry =>
            entryUser(entry)(is => {
              val file = new File(capsuleClasses, entry.getName)
              Files.copy(is, file.toPath)
              file
            })
          }
        }
      }.toList
    }

    classes
  }

  def buildManifest(options: CapsulePackageOptions, caplets: List[String]): Manifest = {
    import collection.JavaConverters._

    val applicationTuple = options.capsuleConfig.application match {
      case MainClassApplication(mc) => CapsuleAttributes.ApplicationClass -> mc
      case ScriptApplication(script) => CapsuleAttributes.ApplicationScript -> script
      case ExecutableJarApplication(path) => CapsuleAttributes.Application -> path
      case MavenApplication(moduleId) => CapsuleAttributes.Application -> moduleId.toMavenCoordinates
    }

    val attributes = Package.ManifestAttributes(
      Attributes.Name.MAIN_CLASS -> "Capsule",
      CapsuleAttributes.ApplicationID -> options.name,
      CapsuleAttributes.ApplicationVersion -> options.version,
      CapsuleAttributes.PreMainClass -> "Capsule",
      CapsuleAttributes.Extract -> options.capsuleConfig.extract.toString
    )

    val manifest = new Manifest
    val manifestAttributes = manifest.getMainAttributes.asScala

    manifestAttributes ++= attributes.attributes
    manifestAttributes += applicationTuple

    if (options.capsuleConfig.vmArgs.nonEmpty) {
      manifestAttributes += CapsuleAttributes.JVMArgs -> options.capsuleConfig.vmArgs.mkString(" ")
    }

    if (options.capsuleConfig.systemProperties.nonEmpty) {
      manifestAttributes += CapsuleAttributes.SystemProperties -> options.capsuleConfig.systemProperties.map { case (k, v) => s"$k=$v" }.mkString(" ")
    }
    options.capsuleConfig.minJavaVersion.foreach{ v =>
      manifestAttributes += CapsuleAttributes.MinJavaVersion -> v
    }
    if (options.capsuleConfig.jdkRequired) {
      manifestAttributes += CapsuleAttributes.JDKRequired -> "true"
    }
    if (options.capsuleConfig.args.nonEmpty) {
      manifestAttributes += CapsuleAttributes.Args -> options.capsuleConfig.args.mkString(" ")
    }
    if (caplets.nonEmpty) {
      manifestAttributes += CapsuleAttributes.Caplets -> caplets.mkString(" ")
    }

    manifest
  }

  def doPackage(packageOptions: CapsulePackageOptions, capsuleClassPath: Classpath, mainJar: File)(implicit log: Logger): File = {

    val jarFile = packageOptions.jarFile
    val capsulesAndCaplets = extractCapsules(packageOptions.target, capsuleClassPath)

    val classpathEntries = List(mainJar -> mainJar.getName) ++ packageOptions.mainClassPath.files.filter(_.getName.endsWith(".jar")).map(f => f -> f.getName)

    val sources = capsulesAndCaplets.map(f => f -> f.getName) ++ classpathEntries

    val caplets = capsulesAndCaplets.map(_.getName.stripSuffix(".class")).filterNot(_ == "Capsule")

    val manifest = buildManifest(packageOptions, caplets)

    Package.makeJar(sources, jarFile, manifest, log)

    jarFile
  }

  case class Capsules(dir: File, classes: List[File])


  object CapsuleAttributes {
    val ApplicationID = new Name("Application-ID")
    val ApplicationVersion = new Name("Application-Version")
    val ApplicationClass = new Name("Application-Class")
    val ApplicationScript = new Name("Application-Script")
    val Application = new Name("Application")
    val Args = new Name("Args")
    val Caplets = new Name("Caplets")
    val MinJavaVersion = new Name("Min-Java-Version")
    val JVMArgs = new Name("JVM-Args")
    val JDKRequired = new Name("JDK-Required")
    val SystemProperties = new Name("System-Properties")
    val JavaAgents = new Name("Java-Agents")
    val PreMainClass = new Name("Premain-Class")
    val Extract = new Name("Extract")
  }

  implicit class ModuleIdToStringOps(mid: ModuleID) {
    def toMavenCoordinates = {
      List(mid.organization, mid.name, mid.revision).mkString(":")
    }
  }
}

