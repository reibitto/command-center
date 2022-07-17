//package commandcenter
//
//import commandcenter.CCRuntime.Env
//import zio.{ExitCode, IO, URIO, ZIO}
//import zio.internal.Platform
//
//trait CCApp extends CCRuntime {
//  def run(args: List[String]): URIO[Env, ExitCode]
//
//  final def main(args0: Array[String]): Unit =
//    try
//      sys.exit(
//        unsafeRun(
//          for {
//            fiber <- run(args0.toList).fork
//            _ <- ZIO.succeed(java.lang.Runtime.getRuntime.addShutdownHook(new Thread {
//
//                   override def run(): Unit = {
//                     val _ = unsafeRunSync(fiber.interrupt)
//                   }
//                 }))
//            result <- fiber.join
//            _      <- fiber.interrupt
//          } yield result.code
//        )
//      )
//    catch { case _: SecurityException => }
//
//  override lazy val platform: Platform = runtime.platform.withReportFailure { c =>
//    if (!c.isInterrupted) {
//      // TODO: Only log to console if running in terminal emulator mode. Because in CLI mode it'll interfere with the UI.
//      // Need a solution for CLI mode. Maybe log into a file or an in-memory ring buffer and then have a separate
//      // command for viewing errors.
//      // System.err.println(c.prettyPrint)
//    }
//  }
//}
// ???
