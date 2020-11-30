package com.itv.scheduler

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.syntax.all._
import org.quartz._

import scala.util.Either

sealed trait PublishCallbackJob extends Job

private[scheduler] sealed abstract class IoPublishCallbackJob[F[_], A](implicit
    F: ConcurrentEffect[F],
    jobDecoder: JobDecoder[A]
) extends PublishCallbackJob {
  def handleMessage: A => F[Unit]

  override def execute(jobExecutionContext: JobExecutionContext): Unit =
    F.toIO(jobDecoder(jobExecutionContext).liftTo[F] >>= handleMessage).unsafeRunSync()
}

final class AutoAckCallbackJob[F[_], A](val handleMessage: A => F[Unit])(implicit
    F: ConcurrentEffect[F],
    jobDecoder: JobDecoder[A]
) extends IoPublishCallbackJob[F, A]

final class ExplicitAckCallbackJob[F[_], A](emitMessage: A => F[Deferred[F, Either[Throwable, Unit]]])(implicit
    F: ConcurrentEffect[F],
    jobDecoder: JobDecoder[A]
) extends IoPublishCallbackJob[F, A] {
  override def handleMessage: A => F[Unit] = emitMessage(_) >>= (_.get.rethrow)
}
