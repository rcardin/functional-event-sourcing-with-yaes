val scala3Version = "3.8.3"

lazy val root = (project in file("."))
  .settings(
    name         := "functional-event-sourcing-with-yaes",
    organization := "in.rcard.fes",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    scalacOptions += "-target:25",
    javacOptions ++= Seq("-source", "25", "-target", "25"),
    libraryDependencies ++= Seq(
      "in.rcard.yaes" %% "yaes-core"  % "0.17.0",
      "org.scalatest"  %% "scalatest" % "3.2.20" % Test
    ),
    Test / logBuffered       := false,
    Test / parallelExecution := false,
    Test / fork              := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
  )
