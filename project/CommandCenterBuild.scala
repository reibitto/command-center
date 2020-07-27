import sbt._
import Keys._
import scala.Console

object CommandCenterBuild {
  val ScalaVersion = "2.13.3"

  val CommandCenterVersion = "0.0.1"

  object Version {
    val zio   = "1.0.0-RC21-2"
    val circe = "0.13.0"

    // If you set this to None you can test with your locally installed version of Graal.
    val imageGraal: Option[String] = None //Some("20.1.0-java11")
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
    "-Xfatal-warnings",
    "-Ymacro-annotations",
    "-Xlint:nullary-unit",           // Warn when nullary methods return Unit.
    "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
    "-Xlint:missing-interpolator",   // A string literal appears to be missing an interpolator id.
    "-Xlint:doc-detached",           // A Scaladoc comment appears to be detached from its element.
    "-Xlint:private-shadow",         // A private field (or class parameter) shadows a superclass field.
    "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
    "-Xlint:delayedinit-select",     // Selecting member of DelayedInit.
    "-Xlint:stars-align",            // Pattern sequence wildcard must align with sequence component.
    "-Xlint:option-implicit",        // Option.apply used implicit view.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
//    "-Xlint:infer-any",                 // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-extra-implicit" // Warn when more than one implicit parameter section is defined.
  ) ++
    Seq(
      "-Ywarn-unused:imports",  // Warn if an import selector is not referenced.
      "-Ywarn-unused:locals",   // Warn if a local definition is unused.
      "-Ywarn-unused:privates", // Warn if a private member is unused.
      "-Ywarn-unused:implicits" // Warn if an implicit parameter is unused.
    ).filter(_ => shouldWarnForUnusedCode) ++
    Seq(
      "-opt:l:inline",
      "-opt-inline-from:**"
    ).filter(_ => shouldOptimize)

  def defaultSettings(projectName: String) = Seq(
    name := projectName,
//    organization := "...",
    scalacOptions := ScalacOptions,
    scalaVersion in ThisBuild := ScalaVersion,
    libraryDependencies ++= Plugins.BaseCompilerPlugins,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true,
    resolvers := Resolvers,
    version := CommandCenterVersion
  )

  lazy val Resolvers = Seq(
    // Order of resolvers affects resolution time. More general purpose repositories should come first.
    Resolver.sonatypeRepo("releases"),
    Resolver.typesafeRepo("releases"),
    Resolver.jcenterRepo,
    Resolver.sonatypeRepo("snapshots")
  )

  def compilerFlag(key: String, default: Boolean): Boolean = {
    val flag = sys.props.get(key).orElse {
      val envVarName = key.replace('.', '_').toUpperCase
      sys.env.get(envVarName)
    }

    val flagValue = flag.map(_.toBoolean).getOrElse(default)

    println(s"${Console.MAGENTA}$key:${Console.RESET} $flagValue")

    flagValue
  }

  lazy val shouldOptimize: Boolean = compilerFlag("scalac.optimize", false)

  lazy val shouldWarnForUnusedCode: Boolean = compilerFlag("scalac.unused.enabled", false)

}
