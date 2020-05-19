addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

organization := "com.itv"

name := "fs2-quartz"

scalaVersion := "2.13.2"

crossScalaVersions := Seq("2.12.11", scalaVersion.value)

resolvers += Resolver.bintrayRepo("janstenpickle", "extruder")

libraryDependencies ++= Seq(
  "org.quartz-scheduler" % "quartz"               % "2.3.2",
  "co.fs2"              %% "fs2-io"               % "2.3.0",
  "io.extruder"         %% "extruder-cats-effect" % "0.11.0",
)
