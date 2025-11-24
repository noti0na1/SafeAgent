name := "safe-agent"
version := "0.1.0"
scalaVersion := "3.8.0-RC1"

libraryDependencies ++= Seq(
  "com.openai" % "openai-java" % "4.8.0",
  "com.lihaoyi" %% "upickle" % "4.4.1",
)

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  // "-source:future",
  "-Yexplicit-nulls",
)
