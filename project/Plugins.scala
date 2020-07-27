import sbt._

object Plugins {
  lazy val BaseCompilerPlugins = Seq(
    compilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    compilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.0" cross CrossVersion.full)
  )
}
