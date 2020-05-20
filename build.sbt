import sbt._

val commonSettings: Seq[Setting[_]] = Seq(
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  organization := "com.itv",
  scalaVersion := "2.13.2",
  crossScalaVersions := Seq("2.12.11", scalaVersion.value),
)

def createProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(commonSettings)
    .settings(name := s"fs2-quartz-$projectName")

lazy val core = createProject("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.quartz-scheduler" % "quartz" % Versions.quartz,
      "co.fs2"              %% "fs2-io" % Versions.fs2,
    ),
  )

lazy val extruder = createProject("extruder")
  .dependsOn(core)
  .settings(
    resolvers += Resolver.bintrayRepo("janstenpickle", "extruder"),
    libraryDependencies ++= Seq(
      "io.extruder"       %% "extruder-cats-effect"            % Versions.extruder,
      "org.scalatest"     %% "scalatest"                       % Versions.scalatest           % Test,
      "org.scalatestplus" %% "scalacheck-1-14"                 % Versions.scalatestScalacheck % Test,
      "com.dimafeng"      %% "testcontainers-scala-scalatest"  % Versions.testContainers      % Test,
      "com.dimafeng"      %% "testcontainers-scala-postgresql" % Versions.testContainers      % Test,
      "org.postgresql"     % "postgresql"                      % "42.2.9"                     % Test,
    ),
  )
