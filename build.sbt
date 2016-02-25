import sbt.Keys._

name := "reactive-gremlin"

version := "1.0"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= deps

scalaVersion := "2.11.7"

fork := true

lazy val deps = {
  val akkaV = "2.4.2"
  val akkaStreamV = "2.0.3"
  Seq(
    "com.typesafe.akka"       %% "akka-actor"                 % akkaV,
    "com.typesafe.akka"       %% "akka-http-experimental"     % akkaV,
    "com.typesafe.play"       %% "play-json"                  % "2.4.4"
  )
}

dependencyOverrides ++= Set(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"
)
