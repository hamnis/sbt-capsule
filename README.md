#SBT Capsule

SBT plugin for working with [Capsules](http://www.capsule.io/)
This plugin is released under the Apache 2.0 License.

To use you just need to add 

addSbtPlugin("net.hamnaberg.sbt" % "sbt-capsule" % "0.1-SNAPSHOT")


And then run 
`sbt capsule:package`

Now a file is generated next to the main artifact.


##Configuring the jar file name.

```scala
capsuleJarFile := crossTarget.value / "demo1-capsule.jar"
```

## Configuring the capsule

```scala
capsuleConfig ~= (config).copy(application = ScriptApplication("meh"))
```

