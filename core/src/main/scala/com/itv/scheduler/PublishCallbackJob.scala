package com.itv.scheduler

import cats.data.Kleisli
import cats.effect.ConcurrentEffect
import cats.implicits._
import org.quartz._

import scala.util.Either

abstract class PublishCallbackJob extends Job {
  def handleMessage: JobExecutionContext => Either[Throwable, Unit]

  override def execute(context: JobExecutionContext): Unit =
    handleMessage(context).valueOr(throw _)
}

final class AutoAckingCallbackJob[F[_], A](emitMessage: Kleisli[F, A, Unit])(implicit
    F: ConcurrentEffect[F],
    jobDecoder: JobDecoder[A]
) extends PublishCallbackJob {
  override def handleMessage: JobExecutionContext => Either[Throwable, Unit] =
    jobExecutionContext => F.toIO(jobDecoder(jobExecutionContext).liftTo[F] >>= emitMessage.run).attempt.unsafeRunSync()
}
