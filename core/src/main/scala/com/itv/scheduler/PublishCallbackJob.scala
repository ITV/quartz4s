package com.itv.scheduler

import cats.effect._
import cats.effect.kernel.{Async, Deferred, Sync}
import cats.effect.unsafe.UnsafeRun
import cats.implicits._
import org.quartz._

import scala.util.Either
import cats.MonadError

sealed trait PublishCallbackJob extends Job

private[scheduler] sealed abstract class IoPublishCallbackJob[F[_]: UnsafeRun, A](implicit
    F: MonadError[F, Throwable],
    jobDecoder: JobDecoder[A]
) extends PublishCallbackJob {
  def handleMessage: A => F[Unit]

  override def execute(jobExecutionContext: JobExecutionContext): Unit =
    UnsafeRun[F].unsafeRunSync(F.fromEither(jobDecoder(jobExecutionContext)) >>= handleMessage)
}

final class AutoAckCallbackJob[F[_]: MonadError[*[_], Throwable]: UnsafeRun, A](val handleMessage: A => F[Unit])(
    implicit jobDecoder: JobDecoder[A]
) extends IoPublishCallbackJob

final class ExplicitAckCallbackJob[F[_]: MonadError[*[_], Throwable]: UnsafeRun, A](
    emitMessage: A => F[Deferred[F, Either[Throwable, Unit]]]
)(implicit jobDecoder: JobDecoder[A])
    extends IoPublishCallbackJob {
  override def handleMessage: A => F[Unit] = emitMessage(_) >>= (_.get.rethrow)
}
