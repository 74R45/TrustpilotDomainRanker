ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.0"

lazy val root = (project in file("."))
  .settings(
    name := "TrustpilotDomainRanker"
  )

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

lazy val akkaVersion = "2.9.1"
lazy val circeVersion = "0.14.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.4.14",
  "net.ruippeixotog" %% "scala-scraper" % "3.1.1",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4"
)

enablePlugins(JavaAppPackaging)

dockerBaseImage := "openjdk:11"