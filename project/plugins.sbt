addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

libraryDependencies <+= sbtVersion("org.scala-sbt" % "scripted-plugin" % _)

addSbtPlugin("no.arktekk.sbt" % "aether-deploy" % "0.17")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")
