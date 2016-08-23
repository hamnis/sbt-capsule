package sbtcapsule

import sbt.Keys._
import sbt._

sealed trait Application

case class MainClassApplication(mainClass: String) extends Application
case class ScriptApplication(script: String) extends Application
case class MavenApplication(moduleID: ModuleID) extends Application
case class ExecutableJarApplication(path: String) extends Application


case class CapsulePackageOptions
(
  capsuleConfig: CapsuleConfig,
  target: File,
  jarFile: File,
  name: String,
  version: String,
  mainClassPath: Classpath
)

case class CapsuleConfig
(
  application: Application,
  extract: Boolean = true,
  vmArgs: List[String] = Nil,
  args: List[String] = Nil,
  systemProperties: Map[String, String] = Map.empty,
  minJavaVersion: Option[String] = None,
  jdkRequired: Boolean = false
)
