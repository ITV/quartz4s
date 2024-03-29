addSbtPlugin("ch.epfl.scala"             % "sbt-bloop"        % "7d2bf0af+20171218-1522")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"     % "2.4.6")
addSbtPlugin("com.github.daniel-shuy"    % "sbt-release-mdoc" % "1.0.1")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"     % "0.1.22")
addSbtPlugin("com.github.sbt"            % "sbt-ci-release"   % "1.5.11")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"      % "0.6.4")

val mdocVersion = "2.3.6"
addSbtPlugin("org.scalameta"            % "sbt-mdoc" % mdocVersion)
libraryDependencies += "org.scalameta" %% "mdoc"     % mdocVersion
