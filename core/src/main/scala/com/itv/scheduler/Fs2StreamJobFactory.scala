package com.itv.scheduler

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.implicits._
import fs2.concurrent.Queue
import org.quartz.{Job, Scheduler}
import org.quartz.simpl.PropertySettingJobFactory
import org.quartz.spi.TriggerFiredBundle

import scala.util.Either

trait CallbackJobFactory extends PropertySettingJobFactory {
  def createCallbackJob: PublishCallbackJob

  override def newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job =
    if (classOf[PublishCallbackJob].isAssignableFrom(bundle.getJobDetail.getJobClass))
      createCallbackJob
    else
      super.newJob(bundle, scheduler)
}

class AutoAckFs2StreamJobFactory[F[_], A](messages: Queue[F, A])(implicit
    F: ConcurrentEffect[F],
    jobDecoder: JobDecoder[A]
) extends CallbackJobFactory {
  override def createCallbackJob: AutoAckCallbackJob[F, A] = new AutoAckCallbackJob[F, A](messages.enqueue1)
}

final case class AckableMessage[F[_], A](message: A, acker: Deferred[F, Either[Throwable, Unit]])

class ExplicitAckFs2StreamJobFactory[F[_], A](messages: Queue[F, AckableMessage[F, A]])(implicit
    F: ConcurrentEffect[F],
    jobDecoder: JobDecoder[A]
) extends CallbackJobFactory {
  override def createCallbackJob: ExplicitAckCallbackJob[F, A] =
    new ExplicitAckCallbackJob[F, A](message =>
      Deferred[F, Either[Throwable, Unit]].flatTap(acker => messages.enqueue1(AckableMessage(message, acker)))
    )
}

object Fs2StreamJobFactory {
  def autoAck[F[_]: ConcurrentEffect, A: JobDecoder](messages: Queue[F, A]): AutoAckFs2StreamJobFactory[F, A] =
    new AutoAckFs2StreamJobFactory[F, A](messages)

  def explicitAck[F[_]: ConcurrentEffect, A: JobDecoder](
      messages: Queue[F, AckableMessage[F, A]]
  ): ExplicitAckFs2StreamJobFactory[F, A] =
    new ExplicitAckFs2StreamJobFactory[F, A](messages)
}
