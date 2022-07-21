import sbt.*
import Keys.*

import scala.Console

object Build {
  val ScalaVersion = "2.13.8"

  val CommandCenterVersion = "0.0.1"

  object Version {
    val zio = "1.0.15"
    val enumeratum = "1.7.0"
    val circe = "0.14.2"
    val sttp = "3.7.1"
    val graal = "20.2.0"
    val swt = "3.120.0"
    val jna = "5.12.1"

    // If you set this to None you can test with your locally installed version of Graal. Otherwise it will run in Docker
    // and build a Linux image (e.g. setting it to s"$graal-java11").
    val imageGraal: Option[String] = None
  }

  lazy val ScalacOptions = Seq(
    "-encoding",
    "UTF-8",
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-Xsource:3",
    "-Xfatal-warnings",
    "-Ymacro-annotations",
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Ywarn-extra-implicit" // Warn when more than one implicit parameter section is defined.
  ) ++
    Seq(
      "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
      "-Ywarn-unused:locals", // Warn if a local definition is unused.
      "-Ywarn-unused:privates", // Warn if a private member is unused.
      "-Ywarn-unused:implicits" // Warn if an implicit parameter is unused.
    ).filter(_ => shouldWarnForUnusedCode) ++
    Seq(
      "-opt:l:inline",
      "-opt-inline-from:**"
    ).filter(_ => shouldOptimize)

  def defaultSettings(projectName: String) =
    Seq(
      name := projectName,
      version := CommandCenterVersion,
      Test / javaOptions += "-Duser.timezone=UTC",
      scalacOptions := ScalacOptions,
      ThisBuild / scalaVersion := ScalaVersion,
      unmanagedBase := baseDirectory.value / "plugins",
      libraryDependencies ++= Plugins.BaseCompilerPlugins,
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-test" % Version.zio % Test,
        "dev.zio" %% "zio-test-sbt" % Version.zio % Test
      ),
      incOptions ~= (_.withLogRecompileOnMacro(false)),
      autoAPIMappings := true,
      resolvers := Resolvers,
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      Test / fork := true,
      Test / logBuffered := false
    )

  // Order of resolvers affects resolution time. More general purpose repositories should come first.
  lazy val Resolvers =
    Resolver.sonatypeOssRepos("releases") ++ Seq(Resolver.typesafeRepo("releases"), Resolver.jcenterRepo) ++ Resolver
      .sonatypeOssRepos("snapshots") :+ Resolver.mavenLocal

  def compilerOption(key: String): Option[String] =
    sys.props.get(key).orElse {
      val envVarName = key.replace('.', '_').replace('-', '_').toUpperCase
      sys.env.get(envVarName)
    }

  def compilerFlag(key: String, default: Boolean): Boolean = {
    val flagValue = compilerOption(key).map(_.toBoolean).getOrElse(default)

    println(s"${scala.Console.MAGENTA}$key:${scala.Console.RESET} $flagValue")

    flagValue
  }

  lazy val shouldOptimize: Boolean = compilerFlag("scalac.optimize", false)

  lazy val shouldWarnForUnusedCode: Boolean = compilerFlag("scalac.unused.enabled", false)

}
