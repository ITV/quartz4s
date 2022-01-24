addSbtPlugin("ch.epfl.scala"             % "sbt-bloop"        % "1032048a")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"     % "2.4.6")
addSbtPlugin("com.github.daniel-shuy"    % "sbt-release-mdoc" % "1.0.1")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"     % "0.1.20")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"      % "0.6.1")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.10")
addSbtPlugin("com.github.sbt" % "sbt-pgp"      % "2.1.2")

val mdocVersion = "2.2.24"
addSbtPlugin("org.scalameta"            % "sbt-mdoc" % mdocVersion)
libraryDependencies += "org.scalameta" %% "mdoc"     % mdocVersion
