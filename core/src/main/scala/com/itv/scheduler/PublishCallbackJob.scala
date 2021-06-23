package com.itv.scheduler

import cats.effect._
import cats.effect.std.Dispatcher
import cats.syntax.all._
import org.quartz._

import scala.util.Either

sealed trait PublishCallbackJob extends Job

private[scheduler] sealed abstract class IoPublishCallbackJob[F[_], A](dispatcher: Dispatcher[F])(implicit
    F: Sync[F],
    jobDecoder: JobDecoder[A]
) extends PublishCallbackJob {
  def handleMessage: A => F[Unit]

  override def execute(jobExecutionContext: JobExecutionContext): Unit =
    dispatcher.unsafeRunSync(jobDecoder(jobExecutionContext).liftTo[F] >>= handleMessage)
}

final class AutoAckCallbackJob[F[_], A](val handleMessage: A => F[Unit], dispatcher: Dispatcher[F])(implicit
    F: Sync[F],
    jobDecoder: JobDecoder[A]
) extends IoPublishCallbackJob[F, A](dispatcher)

final class ExplicitAckCallbackJob[F[_], A](
    emitMessage: A => F[Deferred[F, Either[Throwable, Unit]]],
    dispatcher: Dispatcher[F]
)(implicit
    F: Sync[F],
    jobDecoder: JobDecoder[A]
) extends IoPublishCallbackJob[F, A](dispatcher) {
  override def handleMessage: A => F[Unit] = emitMessage(_) >>= (_.get.rethrow)
}
