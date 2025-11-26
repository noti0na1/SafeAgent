name := "safe-agent"
version := "0.1.0"
scalaVersion := "3.8.0-RC2"

libraryDependencies ++= Seq(
  "com.openai" % "openai-java" % "4.8.0",
  "com.lihaoyi" %% "upickle" % "4.4.1",
  "com.lihaoyi" %% "utest" % "0.9.2" % Test
)

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  // "-source:future",
  "-Yexplicit-nulls",
  // "-Wunused:all",
  "-Wsafe-init",
  // "-Wall",
)

testFrameworks += new TestFramework("utest.runner.Framework")

Compile / scalaSource := baseDirectory.value / "src" / "main" / "scala"
Test / scalaSource := baseDirectory.value / "src" / "test" / "scala"
