import sbt._

Global / bloopExportJarClassifiers := Some(Set("sources"))

val commonSettings: Seq[Setting[_]] = Seq(
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.0" cross CrossVersion.full),
  organization := "com.itv",
  scalaVersion := "2.13.4",
  crossScalaVersions := Seq("2.12.12", scalaVersion.value),
  Global / bloopAggregateSourceDependencies := true,
  credentials ++=
    Seq(".itv-credentials", ".user-credentials", ".credentials")
      .map(fileName => Credentials(Path.userHome / ".ivy2" / fileName)),
  ThisBuild / publishTo := {
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
    .settings(name := s"quartz4s-$projectName")

lazy val root = (project in file("."))
  .aggregate(core, extruder, docs)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
  )

lazy val core = createProject("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.quartz-scheduler"    % "quartz"                          % Versions.quartz exclude ("com.zaxxer", "HikariCP-java7"),
      "org.typelevel"          %% "cats-effect"                     % Versions.catsEffect,
      "org.scala-lang.modules" %% "scala-collection-compat"         % Versions.collectionCompat,
      "org.scalatest"          %% "scalatest"                       % Versions.scalatest           % Test,
      "org.scalatestplus"      %% "scalacheck-1-15"                 % Versions.scalatestScalacheck % Test,
      "com.dimafeng"           %% "testcontainers-scala-scalatest"  % Versions.testContainers      % Test,
      "com.dimafeng"           %% "testcontainers-scala-postgresql" % Versions.testContainers      % Test,
      "org.postgresql"          % "postgresql"                      % Versions.postgresql          % Test,
      "com.zaxxer"              % "HikariCP"                        % Versions.hikari              % Test,
      "org.flywaydb"            % "flyway-core"                     % Versions.flyway              % Test,
      "ch.qos.logback"          % "logback-classic"                 % Versions.logback             % Test,
    ),
  )

lazy val extruder = createProject("extruder")
  .dependsOn(core)
  .settings(
    resolvers += Resolver.bintrayRepo("janstenpickle", "extruder"),
    libraryDependencies ++= Seq(
      "io.extruder"       %% "extruder-core"   % Versions.extruder,
      "org.scalatest"     %% "scalatest"       % Versions.scalatest           % Test,
      "org.scalatestplus" %% "scalacheck-1-15" % Versions.scalatestScalacheck % Test,
      "org.scalamock"     %% "scalamock"       % Versions.scalamock           % Test,
      "ch.qos.logback"     % "logback-classic" % Versions.logback             % Test,
    ),
  )

lazy val docs = project
  .in(file("quartz4s-docs"))
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    mdocOut := (ThisBuild / baseDirectory).value,
    mdocVariables := Map(
      "QUARTZ4S_VERSION" -> version.value
    ),
    releaseProcess := Seq[ReleaseStep](
      ReleasePlugin.autoImport.releaseStepInputTask(MdocPlugin.autoImport.mdoc),
      ReleaseMdocStateTransformations.commitMdoc,
    ),
  )
  .dependsOn(core, extruder)

addCommandAlias("buildQuartz4s", ";clean;+test;mdoc")
