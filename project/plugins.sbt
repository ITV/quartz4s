addSbtPlugin("ch.epfl.scala"          % "sbt-bloop"        % "1.4.1")
addSbtPlugin("org.scalameta"          % "sbt-scalafmt"     % "2.4.0")
addSbtPlugin("com.github.daniel-shuy" % "sbt-release-mdoc" % "1.0.1")

val mdocVersion = "2.2.2"
addSbtPlugin("org.scalameta"            % "sbt-mdoc" % mdocVersion)
libraryDependencies += "org.scalameta" %% "mdoc"     % mdocVersion
