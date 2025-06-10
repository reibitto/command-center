import sbt.*
import sbt.Keys.*
import sbt.Package.ManifestAttributes
import sbtwelcome.*

import java.nio.file.{Files, StandardCopyOption}

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
    addCommandAlias(
      "cleanup",
      "scalafixEnable; scalafix RemoveUnused; Test/scalafix RemoveUnused; fmt"
    ),
    logo :=
      s"""
         |,---.                           .   ,---.         .
         ||     ,-. ,-,-. ,-,-. ,-. ,-. ,-|   |     ,-. ,-. |- ,-. ,-.
         ||     | | | | | | | | ,-| | | | |   |     |-' | | |  |-' |
         |`---' `-' ' ' ' ' ' ' `-^ ' ' `-'   `---' `-' ' ' `' `-' '
         |
         |""".stripMargin,
    usefulTasks := Seq(
      UsefulTask("~compile", "Compile all modules with file-watch enabled"),
      UsefulTask("fmt", "Run scalafmt on the entire project"),
      UsefulTask("cli/run", "Run the Command Center CLI client (interactive mode by default)"),
      UsefulTask("cli/assembly", "Create an executable JAR for running command line utility"),
      UsefulTask(
        "cli/graalvm-native-image:packageBin",
        s"Create a native executable of the CLI client ${scala.Console.RED}(Windows not yet supported)"
      ),
      UsefulTask("emulator-swt/run", "Run the Command Center emulated terminal (SWT)"),
      UsefulTask("emulator-swt/assembly", "Create an executable JAR for running in terminal emulator mode (SWT)"),
      UsefulTask("emulator-swing/run", "Run the Command Center emulated terminal (Swing)"),
      UsefulTask(
        "emulator-swing/assembly",
        "Create an executable JAR for running in terminal emulator mode (Swing)"
      )
    ),
    onLoad := {
      if (enabledPlugins.nonEmpty) {
        val pluginsFormatted = optionalPlugins
          .map(_.project)
          .collect { case p: LocalProject =>
            s"${scala.Console.BLUE}${p.project}${scala.Console.RESET}"
          }
          .mkString(", ")

        println(s"${scala.Console.MAGENTA}Enabled plugins${scala.Console.RESET}: $pluginsFormatted")
      }

      onLoad.value
    }
  )

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % V.zio,
      "dev.zio" %% "zio-streams" % V.zio,
      "dev.zio" %% "zio-logging" % V.zioLogging,
      "dev.zio" %% "zio-prelude" % V.zioPrelude,
      "dev.zio" %% "zio-process" % V.zioProcess,
      "com.github.ben-manes.caffeine" % "caffeine" % "3.2.1",
      "org.scala-lang" % "scala-reflect" % V.scalaReflect,
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-parser" % V.circe,
      "io.circe" %% "circe-config" % V.circeConfig,
      "com.monovore" %% "decline" % V.decline,
      "com.lihaoyi" %% "fansi" % V.fansi,
      "com.beachape" %% "enumeratum" % V.enumeratum,
      "com.beachape" %% "enumeratum-circe" % V.enumeratumCirce,
      "com.softwaremill.sttp.client3" %% "core" % V.sttp,
      "com.softwaremill.sttp.client3" %% "circe" % V.sttp,
      "com.softwaremill.sttp.client3" %% "zio" % V.sttp,
      "com.lihaoyi" %% "fastparse" % V.fastparse,
      "org.typelevel" %% "spire" % V.spire,
      "net.java.dev.jna" % "jna" % V.jna,
      "net.java.dev.jna" % "jna-platform" % V.jna,
      "org.ocpsoft.prettytime" % "prettytime-nlp" % V.prettytime
    ),
    buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, sbtVersion),
    buildInfoPackage := "commandcenter"
  )
  .enablePlugins(BuildInfoPlugin)

lazy val coreUI = module("core-ui")
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "com.googlecode.lanterna" % "lanterna" % V.lanterna
    )
  )

lazy val cli = module("cli")
  .dependsOn(coreUI)
  .settings(
    fork := true,
    run / baseDirectory := file("."),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "svm-subs" % V.graal
    ),
    // Windows native terminal requires JNA.
    libraryDependencies ++= Seq("net.java.dev.jna" % "jna-platform" % V.jna).filter(_ => OS.os == OS.Windows),
    assembly / mainClass := Some("commandcenter.cli.Main"),
    assembly / assemblyJarName := "cc.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.filterDistinctLines
      case PathList("META-INF", "versions", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case _                                    => MergeStrategy.first
    },
    assembly / packageOptions += ManifestAttributes("Multi-Release" -> "true"),
    installCC := installCCTask.value,
    graalVMNativeImageGraalVersion := Build.imageGraal,
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

def optionalPlugin(project: Project): Option[ClasspathDependency] =
  if (enabledPlugins.contains(project.id) || enabledPlugins.contains("*"))
    Some(project: ClasspathDependency)
  else
    None

lazy val emulatorCore = module("emulator-core")
  .dependsOn(coreUI)
  .dependsOn(optionalPlugins *)
  .settings(
    run / baseDirectory := file("."),
    libraryDependencies ++= Seq(
      "com.github.tulskiy" % "jkeymaster" % V.jkeymaster,
      "org.slf4j" % "slf4j-nop" % V.slf4j // Seems to be required for jkeymaster on Linux
    )
  )

lazy val emulatorSwt = module("emulator-swt")
  .dependsOn(emulatorCore)
  .dependsOn(optionalPlugins *)
  .settings(
    publishMavenStyle := false,
    run / baseDirectory := file("."),
    assembly / mainClass := Some("commandcenter.emulator.swt.Main"),
    javaOptions ++= Seq("-XstartOnFirstThread").filter(_ => OS.os == OS.MacOS),
    assembly / assemblyJarName := "cc-swt.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.filterDistinctLines
      case PathList("META-INF", "versions", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case _                                    => MergeStrategy.first
    },
    assembly / packageOptions += ManifestAttributes("Multi-Release" -> "true"),
    installCC := installCCTask.value,
    libraryDependencies ++= swtDependencies
  )

lazy val emulatorSwing = module("emulator-swing")
  .dependsOn(emulatorCore)
  .dependsOn(optionalPlugins *)
  .settings(
    run / baseDirectory := file("."),
    assembly / mainClass := Some("commandcenter.emulator.swing.Main"),
    assembly / assemblyJarName := "cc-swing.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.filterDistinctLines
      case PathList("META-INF", "versions", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case _                                    => MergeStrategy.first
    },
    assembly / packageOptions += ManifestAttributes("Multi-Release" -> "true"),
    installCC := installCCTask.value
  )

lazy val strokeOrderPlugin = module("stroke-order-plugin", Some("extras/stroke-order"))
  .dependsOn(core)

lazy val jectPlugin = module("ject-plugin", Some("extras/ject"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.reibitto" %% "ject-ja" % V.ject,
      "com.github.reibitto" %% "ject-ko" % V.ject
    )
  )

lazy val experimentalPlugins = module("experimental-plugins", Some("extras/experimental"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      "net.ruippeixotog" %% "scala-scraper" % V.scalaScraper,
      "com.lihaoyi" %% "pprint" % V.pprint,
      "com.github.reibitto" %% "ject-ja" % V.ject,
      "com.github.reibitto" %% "ject-ko" % V.ject
    )
  )

lazy val extras = project
  .in(file("extras"))
  .aggregate(optionalPlugins.map(_.project) *)

lazy val optionalPlugins =
  Seq(
    optionalPlugin(strokeOrderPlugin),
    optionalPlugin(jectPlugin),
    optionalPlugin(experimentalPlugins)
  ).flatten

def swtDependencies: Seq[ModuleID] =
  OS.os match {
    case OS.Windows =>
      Seq("org.eclipse.platform" % "org.eclipse.swt.win32.win32.x86_64" % V.swt intransitive ())
    case OS.MacOS =>
      Seq("org.eclipse.platform" % "org.eclipse.swt.cocoa.macosx.x86_64" % V.swt intransitive ())
    case OS.Linux =>
      Seq("org.eclipse.platform" % "org.eclipse.swt.gtk.linux.x86_64" % V.swt intransitive ())
    case OS.Other(name) =>
      println(s"SWT does not support OS '$name'")
      Seq.empty
  }

lazy val installCC = taskKey[Unit]("install Command Center uberjar")

def installCCTask =
  Def.taskDyn {
    Def.task {
      val jarName = (assembly / assemblyJarName).value
      val outputPath = (assembly / assemblyOutputPath).value
      val installDirectoryEnvVarName = "COMMAND_CENTER_INSTALL_DIR"

      sys.env.get(installDirectoryEnvVarName) match {
        case Some(installDirectory) =>
          val installPath = new File(installDirectory, jarName)

          if (outputPath.exists) {
            Files.copy(outputPath.toPath, installPath.toPath, StandardCopyOption.REPLACE_EXISTING)

            println(s"Installed uberjar to: ${installPath.absolutePath}")
          } else {
            println(s"Could not install uberjar because the uberjar was not found at: ${outputPath.absolutePath}")
          }

        case None =>
          println(s"No environment variable found for install directory: $installDirectoryEnvVarName")
      }

    }.dependsOn(assembly)
  }

def module(projectId: String, moduleFile: Option[String] = None): Project =
  Project(id = projectId, base = file(moduleFile.getOrElse(projectId)))
    .settings(Build.defaultSettings(projectId))
