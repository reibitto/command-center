package commandcenter.util

import zio.*

object RuntimeExtensions {

  implicit class RuntimeExtension[R](val self: Runtime[R]) extends AnyVal {

    def unsafeRun[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): Exit[E, A] =
      Unsafe.unsafe(implicit u => self.unsafe.run(zio))

    def unsafeRunOrThrow[A](zio: ZIO[R, Throwable, A])(implicit trace: Trace): A =
      Unsafe.unsafe(implicit u => self.unsafe.run(zio).getOrThrow())

    def unsafeFork[E, A](zio: ZIO[R, E, A])(implicit trace: Trace): Fiber.Runtime[E, A] =
      Unsafe.unsafe(implicit u => self.unsafe.fork(zio))
  }
}
