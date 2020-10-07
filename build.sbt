import sbt._

val commonSettings: Seq[Setting[_]] = Seq(
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  organization := "com.itv",
  scalaVersion := "2.13.2",
  crossScalaVersions := Seq("2.12.11", scalaVersion.value),
  bloopAggregateSourceDependencies in Global := true,
  credentials ++=
    Seq(".itv-credentials", ".user-credentials", ".credentials")
      .map(fileName => Credentials(Path.userHome / ".ivy2" / fileName)),
  publishTo in ThisBuild := {
    val artifactory = "https://itvrepos.jfrog.io/itvrepos/oasvc-ivy"
    if (isSnapshot.value)
      Some("Artifactory Realm" at artifactory)
    else
      Some("Artifactory Realm" at artifactory + ";build.timestamp=" + new java.util.Date().getTime)
  },
)

def createProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(commonSettings)
    .settings(name := s"fs2-quartz-$projectName")

lazy val root = (project in file("."))
  .aggregate(core, extruder, docs)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
  )

lazy val core = createProject("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.quartz-scheduler" % "quartz"                          % Versions.quartz exclude ("com.zaxxer", "HikariCP-java7"),
      "org.typelevel"       %% "cats-effect"                     % Versions.catsEffect,
      "co.fs2"              %% "fs2-io"                          % Versions.fs2,
      "org.scalatest"       %% "scalatest"                       % Versions.scalatest           % Test,
      "org.scalatestplus"   %% "scalacheck-1-14"                 % Versions.scalatestScalacheck % Test,
      "com.dimafeng"        %% "testcontainers-scala-scalatest"  % Versions.testContainers      % Test,
      "com.dimafeng"        %% "testcontainers-scala-postgresql" % Versions.testContainers      % Test,
      "org.postgresql"       % "postgresql"                      % Versions.postgresql          % Test,
      "com.zaxxer"           % "HikariCP"                        % Versions.hikari              % Test,
      "org.flywaydb"         % "flyway-core"                     % Versions.flyway              % Test,
      "ch.qos.logback"       % "logback-classic"                 % Versions.logback             % Test,
    ),
  )

lazy val extruder = createProject("extruder")
  .dependsOn(core)
  .settings(
    resolvers += Resolver.bintrayRepo("janstenpickle", "extruder"),
    libraryDependencies ++= Seq(
      "io.extruder"       %% "extruder-core"   % Versions.extruder,
      "org.scalatest"     %% "scalatest"       % Versions.scalatest           % Test,
      "org.scalatestplus" %% "scalacheck-1-14" % Versions.scalatestScalacheck % Test,
      "org.scalamock"     %% "scalamock"       % Versions.scalamock           % Test,
      "ch.qos.logback"     % "logback-classic" % Versions.logback             % Test,
    ),
  )

lazy val docs = project
  .in(file("fs2-quartz-docs"))
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    skip in publish := true,
    mdocOut := baseDirectory.in(ThisBuild).value,
    mdocVariables := Map(
      "FS2_QUARTZ_VERSION" -> version.value
    ),
    releaseProcess := Seq[ReleaseStep](
      ReleasePlugin.autoImport.releaseStepInputTask(MdocPlugin.autoImport.mdoc),
      ReleaseMdocStateTransformations.commitMdoc,
    ),
  )
  .dependsOn(core, extruder)
