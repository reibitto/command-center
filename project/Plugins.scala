import sbt.*

object Plugins {

  lazy val BaseCompilerPlugins = Seq(
    compilerPlugin("com.hmemcpy" %% "zio-clippy" % "0.0.5"),
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full)
  )
}
