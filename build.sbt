organisation := "com.itv"

name := "fs2-quartz"

version := "0.1"

scalaVersion := "2.13.2"

crossScalaVersions := Seq("2.12.11", scalaVersion.value)

libraryDependencies ++= Seq(
  "org.quartz-scheduler" % "quartz" % "2.3.2"
)
