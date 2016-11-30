import SonatypeKeys._

import sbt.Keys._

sonatypeSettings

name := "reactive-gremlin"

version := "0.0.2-SNAPSHOT"

organization := "io.surfkit"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= deps

scalaVersion := "2.11.8"

fork := true

lazy val deps = {
  val akkaV = "2.4.8"
  Seq(
    "com.typesafe.akka"       %% "akka-actor"                 % akkaV,
    "com.typesafe.akka"       %% "akka-http-experimental"     % akkaV,
    "com.typesafe.play"       %% "play-json"                  % "2.4.4"
  )
}

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
)

homepage := Some(url("http://www.surfkit.io/"))

licenses += ("MIT License", url("http://www.opensource.org/licenses/mit-license.php"))

scmInfo := Some(ScmInfo(
  url("https://github.com/coreyauger/reactive-gremlin"),
  "scm:git:git@github.com/coreyauger/reactive-gremlin.git",
  Some("scm:git:git@github.com:coreyauger/reactive-gremlin.git")))


publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
  <developers>
    <developer>
      <id>coreyauger</id>
      <name>Corey Auger</name>
      <url>https://github.com/coreyauger/</url>
    </developer>
  </developers>
  )

pomIncludeRepository := { _ => false }
