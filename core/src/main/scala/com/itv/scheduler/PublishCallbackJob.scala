package com.itv.scheduler

import cats.MonadThrow
import cats.effect.*
import cats.effect.std.Dispatcher
import cats.syntax.all.*
import org.quartz.*

import scala.util.Either

sealed trait PublishCallbackJob extends Job

private[scheduler] sealed abstract class IoPublishCallbackJob[F[_], A](dispatcher: Dispatcher[F])(implicit
    F: MonadThrow[F],
    jobDecoder: JobDecoder[A]
) extends PublishCallbackJob {
  def handleMessage: A => F[Unit]

  override def execute(jobExecutionContext: JobExecutionContext): Unit =
    dispatcher.unsafeRunSync(jobDecoder.decode(jobExecutionContext).liftTo[F] >>= handleMessage)
}

final class AutoAckCallbackJob[F[_], A](val handleMessage: A => F[Unit], dispatcher: Dispatcher[F])(implicit
    F: MonadThrow[F],
    jobDecoder: JobDecoder[A]
) extends IoPublishCallbackJob[F, A](dispatcher)

final class ExplicitAckCallbackJob[F[_], A](
    emitMessage: A => F[Deferred[F, Either[Throwable, Unit]]],
    dispatcher: Dispatcher[F]
)(implicit
    F: MonadThrow[F],
    jobDecoder: JobDecoder[A]
) extends IoPublishCallbackJob[F, A](dispatcher) {
  override def handleMessage: A => F[Unit] = emitMessage(_) >>= (_.get.rethrow)
}
