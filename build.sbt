import sbt._
import ReleaseTransformations._

Global / bloopExportJarClassifiers := Some(Set("sources"))

val commonSettings: Seq[Setting[_]] = Seq(
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/ITV/quartz4s"),
      "scm:git@github.com:ITV/quartz4s.git"
    )
  ),
  organization                              := "com.itv",
  organizationName                          := "ITV",
  scalaVersion                              := "2.13.5",
  crossScalaVersions                        := Seq("2.12.15", scalaVersion.value),
  Global / bloopAggregateSourceDependencies := true,
  licenses                                  := Seq("ITV-OSS" -> url("http://itv.com/itv-oss-licence-v1.0")),
  ThisBuild / publishTo                     := sonatypePublishToBundle.value,
  ThisBuild / pomIncludeRepository          := { _ => false },
  publishMavenStyle                         := true,
  pomExtra :=
    <url>https://github.com/ITV/quartz4s</url>
      <developers>
        <developer>
          <id>agustafson</id>
          <name>Andrew Gustafson</name>
          <organization>ITV</organization>
          <organizationUrl>http://www.itv.com</organizationUrl>
        </developer>
        <developer>
          <id>jbwheatley</id>
          <name>Jack Wheatley</name>
          <organization>ITV</organization>
          <organizationUrl>http://www.itv.com</organizationUrl>
        </developer>
      </developers>
)

def createProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(commonSettings)
    .settings(name := s"quartz4s-$projectName")

lazy val root = (project in file("."))
  .aggregate(core, docs)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
  )

lazy val core = createProject("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.quartz-scheduler"    % "quartz"                  % Versions.quartz exclude ("com.zaxxer", "HikariCP-java7"),
      "org.typelevel"          %% "cats-effect"             % Versions.catsEffect,
      "com.chuusai"            %% "shapeless"               % Versions.shapeless,
      "org.scala-lang.modules" %% "scala-collection-compat" % Versions.collectionCompat,
      "org.scalatest"          %% "scalatest"               % Versions.scalatest           % Test,
      "org.scalatestplus"      %% "scalacheck-1-15"         % Versions.scalatestScalacheck % Test,
      "org.scalameta"          %% "munit"                   % Versions.munit               % Test,
      "com.dimafeng"               %% "testcontainers-scala-scalatest"  % Versions.testContainers      % Test,
      "com.dimafeng"               %% "testcontainers-scala-postgresql" % Versions.testContainers      % Test,
      "org.postgresql"              % "postgresql"                      % Versions.postgresql          % Test,
      "com.zaxxer"                  % "HikariCP"                        % Versions.hikari              % Test,
      "org.flywaydb"                % "flyway-core"                     % Versions.flyway              % Test,
      "ch.qos.logback"              % "logback-classic"                 % Versions.logback             % Test,
      "org.scalamock"              %% "scalamock"                       % Versions.scalamock           % Test,
      "org.typelevel"              %% "cats-laws"                       % Versions.cats                % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.15"       % Versions.scalacheckShapeless % Test,
      "org.typelevel"              %% "discipline-munit"                % Versions.disciplineMunit     % Test,
    ),
  )

lazy val docs = project
  .in(file("quartz4s-docs"))
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    mdocOut        := (ThisBuild / baseDirectory).value,
    mdocVariables := Map(
      "QUARTZ4S_VERSION" -> version.value
    ),
    releaseProcess := Seq[ReleaseStep](
      ReleasePlugin.autoImport.releaseStepInputTask(MdocPlugin.autoImport.mdoc),
      ReleaseMdocStateTransformations.commitMdoc,
    ),
  )
  .dependsOn(core)

addCommandAlias("buildQuartz4s", ";clean;+test;mdoc")

releaseCrossBuild := true // true if you cross-build the project for multiple Scala versions
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
//  releaseStepInputTask(MdocPlugin.autoImport.mdoc),
//  ReleaseMdocStateTransformations.commitMdoc,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
