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
    emulatorSwt,
    emulatorSwing
  )
  .settings(
    name := "command-center",
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
      UsefulTask("f", "emulator-swt/run", "Run the Command Center emulated terminal (SWT)"),
      UsefulTask("g", "emulator-swt/assembly", "Create an executable JAR for running in terminal emulator mode (SWT)"),
      UsefulTask("h", "emulator-swing/run", "Run the Command Center emulated terminal (Swing)"),
      UsefulTask(
        "i",
        "emulator-swing/assembly",
        "Create an executable JAR for running in terminal emulator mode (Swing)"
      )
    )
  )

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                      %% "zio"                    % Version.zio,
      "dev.zio"                      %% "zio-streams"            % Version.zio,
      "dev.zio"                      %% "zio-process"            % "0.3.0",
      "dev.zio"                      %% "zio-logging"            % "0.5.8",
      "io.circe"                     %% "circe-config"           % "0.8.0",
      "org.scala-lang"                % "scala-reflect"          % "2.13.5",
      "io.circe"                     %% "circe-core"             % Version.circe,
      "io.circe"                     %% "circe-parser"           % Version.circe,
      "com.monovore"                 %% "decline"                % "1.4.0",
      "com.lihaoyi"                  %% "fansi"                  % "0.2.11",
      "com.beachape"                 %% "enumeratum"             % Version.enumeratum,
      "com.beachape"                 %% "enumeratum-circe"       % Version.enumeratum,
      "com.softwaremill.sttp.client" %% "core"                   % Version.sttp,
      "com.softwaremill.sttp.client" %% "circe"                  % Version.sttp,
      "com.softwaremill.sttp.client" %% "httpclient-backend-zio" % Version.sttp,
      "com.lihaoyi"                  %% "fastparse"              % "2.3.2",
      "org.typelevel"                %% "spire"                  % "0.17.0",
      "org.cache2k"                   % "cache2k-core"           % "1.6.0.Final",
      "net.java.dev.jna"              % "jna"                    % Version.jna,
      "net.java.dev.jna"              % "jna-platform"           % Version.jna
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
    libraryDependencies ++= Seq("net.java.dev.jna" % "jna-platform" % Version.jna).filter(_ => OS.os == OS.Windows),
    mainClass in assembly := Some("commandcenter.cli.Main"),
    assemblyJarName in assembly := "cc.jar",
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

lazy val emulatorCore = module("emulator-core")
  .dependsOn(coreUI)
  .dependsOn(
    (optionalPlugin(strokeOrderPlugin) ++ optionalPlugin(jectPlugin)).toSeq: _*
  )
  .settings(
    fork := true,
    baseDirectory in run := file("."),
    libraryDependencies ++= Seq(
      "com.github.tulskiy" % "jkeymaster" % "1.3",
      "org.slf4j"          % "slf4j-nop"  % "1.7.30" // Seems to be required for jkeymaster on Linux
    )
  )

lazy val emulatorSwt = module("emulator-swt")
  .dependsOn(emulatorCore)
  .dependsOn(
    (optionalPlugin(strokeOrderPlugin) ++ optionalPlugin(jectPlugin)).toSeq: _*
  )
  .settings(
    fork := true,
    publishMavenStyle := false,
    baseDirectory in run := file("."),
    mainClass in assembly := Some("commandcenter.emulator.swt.Main"),
    javaOptions := Seq("-XstartOnFirstThread").filter(_ => OS.os == OS.MacOS),
    assemblyJarName in assembly := "cc-swt.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "services", _ @_*) => MergeStrategy.filterDistinctLines
      case PathList("META-INF", _ @_*)             => MergeStrategy.discard
      case _                                       => MergeStrategy.first
    },
    libraryDependencies ++= swtDependencies
  )

lazy val emulatorSwing = module("emulator-swing")
  .dependsOn(emulatorCore)
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
    }
  )

lazy val strokeOrderPlugin = module("stroke-order-plugin", Some("extras/stroke-order"))
  .dependsOn(core)

lazy val jectPlugin = module("ject-plugin", Some("extras/ject"))
  .dependsOn(core)
  .settings(libraryDependencies ++= Seq("com.github.reibitto" %% "ject" % "0.0.1"))

lazy val extras     = project
  .in(file("extras"))
  .aggregate(strokeOrderPlugin, jectPlugin)

def swtDependencies: Seq[ModuleID] =
  OS.os match {
    case OS.Windows     =>
      Seq("org.eclipse.platform" % "org.eclipse.swt.win32.win32.x86_64" % Version.swt intransitive ())
    case OS.MacOS       =>
      Seq("org.eclipse.platform" % "org.eclipse.swt.cocoa.macosx.x86_64" % Version.swt intransitive ())
    case OS.Linux       =>
      Seq("org.eclipse.platform" % "org.eclipse.swt.gtk.linux.x86_64" % Version.swt intransitive ())
    case OS.Other(name) =>
      println(s"SWT does not support OS '$name'")
      Seq.empty
  }

def module(projectId: String, moduleFile: Option[String] = None): Project =
  Project(id = projectId, base = file(moduleFile.getOrElse(projectId)))
    .settings(Build.defaultSettings(projectId))
