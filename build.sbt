import CommandCenterBuild.Version
import sbt.Keys._
import sbt._
import sbtwelcome._

lazy val root = project
  .in(file("."))
  .aggregate(
    core,
    coreUI,
    cliClient,
    daemon
  )
  .settings(
    addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll"),
    addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll"),
    logo :=
      """
        |,---.                           .   ,---.         .
        ||     ,-. ,-,-. ,-,-. ,-. ,-. ,-|   |     ,-. ,-. |- ,-. ,-.
        ||     | | | | | | | | ,-| | | | |   |     |-' | | |  |-' |
        |`---' `-' ' ' ' ' ' ' `-^ ' ' `-'   `---' `-' ' ' `' `-' '  """.stripMargin,
    usefulTasks := Seq(
      UsefulTask("a", "~compile", "Compile all modules with file-watch enabled"),
      UsefulTask("b", "fmt", "Run scalafmt on the entire project"),
      UsefulTask("c", "cli-client/run", "Run Command Center CLI client (interactive mode by default)"),
      UsefulTask("d", "cli-client/assembly", "Create an executable JAR for running command line utility"),
      UsefulTask("e", "cli-client/graalvm-native-image:packageBin", "Create a native executable of the CLI client"),
      UsefulTask("f", "daemon/assembly", "Create an executable JAR for running in daemon mode")
    )
  )

lazy val core = module("core")
  .settings(
    resolvers := Resolvers,
    libraryDependencies ++= Seq(
      "dev.zio"                      %% "zio"                    % Version.zio,
      "dev.zio"                      %% "zio-streams"            % Version.zio,
      "dev.zio"                      %% "zio-process"            % "0.1.0",
      "dev.zio"                      %% "zio-logging"            % "0.4.0",
      "io.circe"                     %% "circe-config"           % "0.8.0",
      "org.scala-lang"                % "scala-reflect"          % "2.13.3",
      "io.circe"                     %% "circe-core"             % Version.circe,
      "io.circe"                     %% "circe-parser"           % Version.circe,
      "com.monovore"                 %% "decline"                % "1.2.0",
      "com.lihaoyi"                  %% "fansi"                  % "0.2.9",
      "com.beachape"                 %% "enumeratum"             % "1.6.1",
      "com.softwaremill.sttp.client" %% "core"                   % Version.sttp,
      "com.softwaremill.sttp.client" %% "circe"                  % Version.sttp,
      "com.softwaremill.sttp.client" %% "httpclient-backend-zio" % Version.sttp,
      "com.lihaoyi"                  %% "fastparse"              % "2.3.0"
    )
  )

lazy val coreUI = module("core-ui")
  .dependsOn(core)
  .settings(
    resolvers := Resolvers,
    libraryDependencies ++= Seq(
      "com.googlecode.lanterna" % "lanterna" % "3.1.0-beta2"
    )
  )

lazy val cliClient = module("cli-client")
  .dependsOn(coreUI)
  .settings(
    mainClass in assembly := Some("commandcenter.cli.Main"),
    assemblyJarName in assembly := "ccc.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", _ @_*) => MergeStrategy.discard
      case _                           => MergeStrategy.first
    },
    resolvers := Resolvers,
    graalVMNativeImageGraalVersion := Version.imageGraal,
    graalVMNativeImageOptions ++= Seq(
      "-H:+ReportExceptionStackTraces",
      "-H:+TraceClassInitialization",
      "-H:IncludeResources=core/src/main/resources/*",
      "--initialize-at-build-time",
      "--no-fallback",
      "--enable-https"
    )
  )
  .enablePlugins(GraalVMNativeImagePlugin, JavaServerAppPackaging)

lazy val daemon = module("daemon")
  .dependsOn(coreUI)
  .settings(
    fork := true,
    mainClass in assembly := Some("commandcenter.daemon.Main"),
    assemblyJarName in assembly := "ccd.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", _ @_*) => MergeStrategy.discard
      case _                           => MergeStrategy.first
    },
    resolvers := Resolvers,
    libraryDependencies ++= Seq(
      "com.github.tulskiy" % "jkeymaster" % "1.3",
      "org.slf4j"          % "slf4j-api"  % "1.7.30" // Seems to be required for jkeymaster on Linux
    )
  )

lazy val Resolvers = Seq(
  // Order of resolvers affects resolution time. More general purpose repositories should come first.
  Resolver.sonatypeRepo("releases"),
  Resolver.typesafeRepo("releases"),
  Resolver.mavenLocal
)

def module(projectId: String): Project =
  Project(id = projectId, base = file(projectId))
    .settings(CommandCenterBuild.defaultSettings(projectId))
