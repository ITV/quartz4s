addSbtPlugin("ch.epfl.scala"             % "sbt-bloop"        % "1.5.4")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"     % "2.4.6")
addSbtPlugin("com.github.daniel-shuy"    % "sbt-release-mdoc" % "1.0.1")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"     % "0.1.22")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"      % "0.6.1")
addSbtPlugin("com.github.sbt"            % "sbt-ci-release"   % "1.5.11")

val mdocVersion = "2.3.6"
addSbtPlugin("org.scalameta"            % "sbt-mdoc" % mdocVersion)
libraryDependencies += "org.scalameta" %% "mdoc"     % mdocVersion
