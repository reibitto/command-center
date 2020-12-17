import Build.Version
import sbt.Keys._
import sbt._
import sbtwelcome._

lazy val root = project
  .in(file("."))
  .aggregate(
    core,
    coreUI,
    cli,
    emulatorSwing
  )
  .settings(
    addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll"),
    addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll"),
    logo :=
      s"""
         |,---.                           .   ,---.         .
         ||     ,-. ,-,-. ,-,-. ,-. ,-. ,-|   |     ,-. ,-. |- ,-. ,-.
         ||     | | | | | | | | ,-| | | | |   |     |-' | | |  |-' |
         |`---' `-' ' ' ' ' ' ' `-^ ' ' `-'   `---' `-' ' ' `' `-' '
         |
         |""".stripMargin,
    usefulTasks := Seq(
      UsefulTask("a", "~compile", "Compile all modules with file-watch enabled"),
      UsefulTask("b", "fmt", "Run scalafmt on the entire project"),
      UsefulTask("c", "cli/run", "Run the Command Center CLI client (interactive mode by default)"),
      UsefulTask("d", "cli/assembly", "Create an executable JAR for running command line utility"),
      UsefulTask(
        "e",
        "cli/graalvm-native-image:packageBin",
        s"Create a native executable of the CLI client ${scala.Console.RED}(Windows not yet supported)"
      ),
      UsefulTask("f", "emulator-swing/run", "Run the Command Center emulated terminal"),
      UsefulTask("g", "emulator-swing/assembly", "Create an executable JAR for running in terminal emulator mode")
    )
  )

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                      %% "zio"                    % Version.zio,
      "dev.zio"                      %% "zio-streams"            % Version.zio,
      "dev.zio"                      %% "zio-process"            % "0.3.0",
      "dev.zio"                      %% "zio-logging"            % "0.5.4",
      "io.circe"                     %% "circe-config"           % "0.8.0",
      "org.scala-lang"                % "scala-reflect"          % "2.13.3",
      "io.circe"                     %% "circe-core"             % Version.circe,
      "io.circe"                     %% "circe-parser"           % Version.circe,
      "com.monovore"                 %% "decline"                % "1.3.0",
      "com.lihaoyi"                  %% "fansi"                  % "0.2.9",
      "com.beachape"                 %% "enumeratum"             % "1.6.1",
      "com.softwaremill.sttp.client" %% "core"                   % Version.sttp,
      "com.softwaremill.sttp.client" %% "circe"                  % Version.sttp,
      "com.softwaremill.sttp.client" %% "httpclient-backend-zio" % Version.sttp,
      "com.lihaoyi"                  %% "fastparse"              % "2.3.0",
      "org.typelevel"                %% "spire"                  % "0.17.0",
      "org.cache2k"                   % "cache2k-core"           % "2.0.0.Final"
    ),
    buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, sbtVersion),
    buildInfoPackage := "commandcenter"
  )
  .enablePlugins(BuildInfoPlugin)

lazy val coreUI = module("core-ui")
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "com.googlecode.lanterna" % "lanterna" % "3.2.0-alpha1"
    )
  )

lazy val cli = module("cli")
  .dependsOn(coreUI)
  .settings(
    fork := true,
    baseDirectory in run := file("."),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "svm-subs" % Version.graal,
      "org.zeromq"     % "jeromq"   % "0.5.2"
    ),
    // Windows native terminal requires JNA.
    libraryDependencies ++= Seq("net.java.dev.jna" % "jna-platform" % "5.6.0").filter(_ => OS.os == OS.Windows),
    mainClass in assembly := Some("commandcenter.cli.Main"),
    assemblyJarName in assembly := "ccc.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", _ @_*) => MergeStrategy.discard
      case _                           => MergeStrategy.first
    },
    graalVMNativeImageGraalVersion := Version.imageGraal,
    graalVMNativeImageOptions ++= Seq(
      "-H:+ReportExceptionStackTraces",
      "-H:+TraceClassInitialization",
      "-H:IncludeResources=lipsum",
      "-H:IncludeResources=applescript/.*",
      "--rerun-class-initialization-at-runtime=sun.security.provider.NativePRNG", // TODO: This is deprecated and doesn't quite work. Will need a workaround.
      "--initialize-at-run-time=com.googlecode.lanterna.terminal.win32.WindowsTerminal",
      "--initialize-at-build-time",
      "--no-fallback",
      "--enable-https",
      // The following unsafe flags are not ideal, but are currently needed until a solution for WindowsTerminal breaking the build is found
      "--allow-incomplete-classpath",
      "--report-unsupported-elements-at-runtime"
    )
  )
  .enablePlugins(GraalVMNativeImagePlugin, JavaServerAppPackaging)

lazy val enabledPlugins: Set[String] =
  Build.compilerOption("command-center-plugins").map(_.split(',').map(_.trim).toSet).getOrElse(Set.empty)

def optionalPlugin(project: Project): Option[ClasspathDependency] = {
  val cp =
    if (enabledPlugins.contains(project.id) || enabledPlugins.contains("*")) Some(project: ClasspathDependency)
    else None
  println(s"${scala.Console.CYAN}${project.id} enabled?${scala.Console.RESET} ${cp.isDefined}")
  cp
}

lazy val emulatorSwing = module("emulator-swing")
  .dependsOn(coreUI)
  .dependsOn(
    (optionalPlugin(strokeOrderPlugin) ++ optionalPlugin(jectPlugin)).toSeq: _*
  )
  .settings(
    fork := true,
    baseDirectory in run := file("."),
    mainClass in assembly := Some("commandcenter.emulator.swing.Main"),
    assemblyJarName in assembly := "cc-swing.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "services", _ @_*) => MergeStrategy.filterDistinctLines
      case PathList("META-INF", _ @_*)             => MergeStrategy.discard
      case _                                       => MergeStrategy.first
    },
    libraryDependencies ++= Seq(
      "com.github.tulskiy" % "jkeymaster" % "1.3",
      "org.slf4j"          % "slf4j-nop"  % "1.7.30" // Seems to be required for jkeymaster on Linux
    )
  )

lazy val strokeOrderPlugin = module("stroke-order-plugin", Some("extras/stroke-order"))
  .dependsOn(core)

lazy val jectPlugin = module("ject-plugin", Some("extras/ject"))
  .dependsOn(core)
  .settings(libraryDependencies ++= Seq("com.github.reibitto" %% "ject" % "0.0.1"))

lazy val extras     = project
  .in(file("extras"))
  .aggregate(strokeOrderPlugin, jectPlugin)

def module(projectId: String, moduleFile: Option[String] = None): Project =
  Project(id = projectId, base = file(moduleFile.getOrElse(projectId)))
    .settings(Build.defaultSettings(projectId))
