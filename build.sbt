import sbt._
import ReleaseTransformations._

Global / onChangedBuildSource := ReloadOnSourceChanges

Global / bloopExportJarClassifiers := Some(Set("sources"))

val commonSettings: Seq[Setting[_]] = Seq(
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  ).filterNot(_ => scalaVersion.value.startsWith("3.")),
  scalacOptions ++= {
    if (scalaVersion.value.startsWith("3.")) Nil
    else Seq("-Ytasty-reader", "-Xsource:3", """-Wconf:msg=package object inheritance is deprecated:i""")
  },
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/ITV/quartz4s"),
      "scm:git@github.com:ITV/quartz4s.git"
    )
  ),
  organization                              := "com.itv",
  organizationName                          := "ITV",
  scalaVersion                              := "3.1.0",
  crossScalaVersions                        := Seq("2.13.8", scalaVersion.value),
  Global / bloopAggregateSourceDependencies := true,
  licenses                                  := Seq("ITV-OSS" -> url("http://itv.com/itv-oss-licence-v1.0")),
  ThisBuild / publishTo                     := sonatypePublishToBundle.value,
  ThisBuild / pomIncludeRepository          := { _ => false },
  publishMavenStyle                         := true,
  pomExtra                                  :=
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
      <developer>
        <id>adamkingitv</id>
        <name>Adam King</name>
        <organization>ITV</organization>
        <organizationUrl>http://www.itv.com</organizationUrl>
      </developer>
    </developers>,
  credentials ++= {
    sys.env
      .get("SONATYPE_USER")
      .zip(sys.env.get("SONATYPE_PASSWORD"))
      .map { case (user, pass) =>
        Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
      }
      .toSeq
  }
)

def createProject(projectName: String): Project =
  Project(projectName, file(projectName))
    .settings(commonSettings)
    .settings(name := s"quartz4s-$projectName")

lazy val root = (project in file("."))
  .aggregate(core, docs)
  .settings(commonSettings)
  .settings(publish / skip := true)

lazy val core = createProject("core")
  .settings(
    ThisBuild / packageDoc / publishArtifact := true,
    libraryDependencies ++= Seq(
      "org.quartz-scheduler"    % "quartz"                  % Versions.quartz exclude ("com.zaxxer", "HikariCP-java7"),
      "org.typelevel"          %% "cats-effect"             % Versions.catsEffect,
      "org.scalatest"          %% "scalatest"               % Versions.scalatest           % Test,
      "org.scalatestplus"      %% "scalacheck-1-15"         % Versions.scalatestScalacheck % Test,
      "org.scalameta"          %% "munit"                   % Versions.munit               % Test,
      "com.dimafeng"  %% "testcontainers-scala-scalatest"  % Versions.testContainers % Test,
      "com.dimafeng"  %% "testcontainers-scala-postgresql" % Versions.testContainers % Test,
      "org.postgresql" % "postgresql"                      % Versions.postgresql     % Test,
      "com.zaxxer"     % "HikariCP"                        % Versions.hikari         % Test,
      "org.flywaydb"   % "flyway-core"                     % Versions.flyway         % Test,
      "ch.qos.logback" % "logback-classic"                 % Versions.logback        % Test,
      ("org.scalamock" %% "scalamock"        % Versions.scalamock       % Test).cross(CrossVersion.for3Use2_13),
      "org.typelevel"  %% "cats-laws"        % Versions.cats            % Test,
      "org.typelevel"  %% "discipline-munit" % Versions.disciplineMunit % Test,
    ) ++ {
      if (scalaVersion.value.startsWith("3.")) {
        Seq(
          "org.typelevel" %% "shapeless3-deriving" % Versions.shapeless3,
        )
      } else {
        Seq(
          "com.chuusai"                %% "shapeless"                 % Versions.shapeless2,
          "com.github.alexarchambault" %% "scalacheck-shapeless_1.15" % Versions.scalacheckShapeless % Test
        )
      }
    },
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
    )
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
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
