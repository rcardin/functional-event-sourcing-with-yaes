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
    val yaesVersion       = "0.20.0"
    val yaesCore          = "in.rcard.yaes"         %% "yaes-core"        % yaesVersion
    val yaesHttpServer    = "in.rcard.yaes"         %% "yaes-http-server" % yaesVersion
    val yaesHttpCirce     = "in.rcard.yaes"         %% "yaes-http-circe"  % yaesVersion
    val yaesSlf4j         = "in.rcard.yaes"         %% "yaes-slf4j"       % yaesVersion
    val yaesHttpClient    = "in.rcard.yaes"         %% "yaes-http-client" % yaesVersion
    val yaesCoreScalatest = "in.rcard.yaes" %% "yaes-core-test-scalatest" % yaesVersion
    val yaesHttpScalatest = "in.rcard.yaes" %% "yaes-http-test-scalatest" % yaesVersion
    val postgresVersion   = "42.7.4"
    val postgres          = "org.postgresql"        %  "postgresql"                    % postgresVersion
    val hikariVersion     = "5.1.0"
    val hikari            = "com.zaxxer"            %  "HikariCP"                      % hikariVersion
    val flywayVersion     = "10.15.0"
    val flywayCore        = "org.flywaydb"          %  "flyway-core"                   % flywayVersion
    val flywayPostgres    = "org.flywaydb"          %  "flyway-database-postgresql"    % flywayVersion
    val testcontainersVersion   = "0.44.1"
    val testcontainersScalatest =
      "com.dimafeng" %% "testcontainers-scala-scalatest"  % testcontainersVersion
    val testcontainersPostgres  =
      "com.dimafeng" %% "testcontainers-scala-postgresql" % testcontainersVersion
  }

// Custom "it" (integration-test) sbt config. sbt's built-in IntegrationTest config was
// deprecated in sbt 1.9 (repo is on 1.12.9), so we define our own. `extend Test` gives it the
// full Test classpath (testcontainers, scalatest, the domain code) with no extra dependency
// wiring. src/it/scala holds the Docker-dependent Testcontainers tests; `sbt test` never
// touches them (fast, Docker-free tier), `sbt It/test` runs only them (real PG tier).
lazy val It = config("it") extend Test

lazy val root = (project in file("."))
  .configs(It)
  .settings(
    name         := "functional-event-sourcing-with-yaes",
    organization := "in.rcard.fes",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    scalacOptions += "-release:25",
    scalacOptions += "-Werror",
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
      dependencies.postgres,
      dependencies.hikari,
      dependencies.flywayCore,
      dependencies.flywayPostgres,
      dependencies.scalatest             % Test,
      dependencies.yaesCoreScalatest     % Test,
      dependencies.yaesHttpScalatest     % Test,
      dependencies.testcontainersScalatest % Test,
      dependencies.testcontainersPostgres  % Test
    ),
    Test / logBuffered       := false,
    Test / parallelExecution := false,
    Test / fork              := true,
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    // The It config mirrors Test's fork + serial execution (container startup is not parallel-safe).
    inConfig(It)(Defaults.testSettings),
    It / fork              := true,
    It / parallelExecution := false,
    It / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
  )
