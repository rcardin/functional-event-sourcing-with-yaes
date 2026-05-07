val scala3Version = "3.8.3"

lazy val dependencies =
  new {
    val scalatestVersion  = "3.2.20"
    val scalatest         = "org.scalatest"         %% "scalatest"        % scalatestVersion
    val circeVersion      = "0.14.15"
    val circe             = "io.circe"              %% "circe-generic"    % circeVersion
    val ironVersion       = "3.3.1"
    val ironCirce         = "io.github.iltotore"    %% "iron-circe"       % ironVersion
    val pureConfigVersion = "0.17.10"
    val pureConfig        = "com.github.pureconfig" %% "pureconfig-core"  % pureConfigVersion
    val ironPureConfig    = "io.github.iltotore"    %% "iron-pureconfig"  % ironVersion
    val yaesVersion       = "0.18.0"
    val yaesCore          = "in.rcard.yaes"         %% "yaes-core"        % yaesVersion
    val yaesHttpServer    = "in.rcard.yaes"         %% "yaes-http-server" % yaesVersion
    val yaesHttpCirce     = "in.rcard.yaes"         %% "yaes-http-circe"  % yaesVersion
    val yaesSlf4j         = "in.rcard.yaes"         %% "yaes-slf4j"       % yaesVersion
    val yaesHttpClient    = "in.rcard.yaes"         %% "yaes-http-client" % yaesVersion
  }

lazy val root = (project in file("."))
  .settings(
    name         := "functional-event-sourcing-with-yaes",
    organization := "in.rcard.fes",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    scalacOptions += "-target:25",
    javacOptions ++= Seq("-source", "25", "-target", "25"),
    libraryDependencies ++= Seq(
      dependencies.yaesCore,
      dependencies.yaesSlf4j,
      dependencies.yaesHttpServer,
      dependencies.yaesHttpClient,
      dependencies.yaesHttpCirce,
      dependencies.circe,
      dependencies.ironCirce,
      dependencies.pureConfig,
      dependencies.ironPureConfig,
      dependencies.scalatest % Test
    ),
    Test / logBuffered       := false,
    Test / parallelExecution := false,
    Test / fork              := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
  )
