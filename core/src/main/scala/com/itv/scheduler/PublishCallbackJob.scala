package com.itv.scheduler

import cats.effect.kernel.Deferred
import cats.effect.std.Dispatcher
import cats.syntax.all._
import org.quartz._

import scala.util.Either
import cats.MonadError

sealed trait PublishCallbackJob extends Job

private[scheduler] sealed abstract class IoPublishCallbackJob[F[_], A](dispatcher: Dispatcher[F])(implicit
    F: MonadError[F, Throwable],
    jobDecoder: JobDecoder[A]
) extends PublishCallbackJob {
  def handleMessage: A => F[Unit]

  override def execute(jobExecutionContext: JobExecutionContext): Unit =
    dispatcher.unsafeRunSync(F.fromEither(jobDecoder(jobExecutionContext)) >>= handleMessage)
}

final class AutoAckCallbackJob[F[_]: MonadError[*[_], Throwable], A: JobDecoder](
    dispatcher: Dispatcher[F],
    val handleMessage: A => F[Unit]
) extends IoPublishCallbackJob[F, A](dispatcher)

final class ExplicitAckCallbackJob[F[_]: MonadError[*[_], Throwable], A](
    dispatcher: Dispatcher[F],
    emitMessage: A => F[Deferred[F, Either[Throwable, Unit]]]
)(implicit jobDecoder: JobDecoder[A])
    extends IoPublishCallbackJob[F, A](dispatcher) {
  override def handleMessage: A => F[Unit] = emitMessage(_) >>= (_.get.rethrow)
}
