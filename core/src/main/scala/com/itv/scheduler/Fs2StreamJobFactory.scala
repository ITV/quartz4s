package com.itv.scheduler

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.syntax.all._
import fs2.concurrent.Queue
import org.quartz.{Job, Scheduler}
import org.quartz.simpl.PropertySettingJobFactory
import org.quartz.spi.TriggerFiredBundle

import scala.concurrent.CancellationException

trait CallbackJobFactory extends PropertySettingJobFactory {
  def createCallbackJob: PublishCallbackJob

  override def newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job =
    if (classOf[PublishCallbackJob].isAssignableFrom(bundle.getJobDetail.getJobClass))
      createCallbackJob
    else
      super.newJob(bundle, scheduler)
}

trait MessageQueueJobFactory[F[_], A] extends CallbackJobFactory {
  def messages: Queue[F, A]
}

class AutoAckingQueueJobFactory[F[_]: ConcurrentEffect, A: JobDecoder](
    override val messages: Queue[F, A]
) extends MessageQueueJobFactory[F, A] {
  override def createCallbackJob: AutoAckCallbackJob[F, A] = new AutoAckCallbackJob[F, A](messages.enqueue1)
}

final case class AckableMessage[F[_], A](message: A, acker: MessageAcker[F, A])

class AckingQueueJobFactory[F[_]: ConcurrentEffect, M[*[_], _], A: JobDecoder](
    override val messages: Queue[F, M[F, A]],
    messageConverter: (A, Deferred[F, Either[Throwable, Unit]]) => M[F, A]
) extends MessageQueueJobFactory[F, M[F, A]] {
  override def createCallbackJob: ExplicitAckCallbackJob[F, A] =
    new ExplicitAckCallbackJob[F, A](message =>
      Deferred[F, Either[Throwable, Unit]].flatTap(acker => messages.enqueue1(messageConverter(message, acker)))
    )
}

object Fs2StreamJobFactory {
  def autoAcking[F[_]: ConcurrentEffect, A: JobDecoder](messages: Queue[F, A]): AutoAckingQueueJobFactory[F, A] =
    new AutoAckingQueueJobFactory[F, A](messages)

  def acking[F[_]: ConcurrentEffect, A: JobDecoder](
      messages: Queue[F, AckableMessage[F, A]]
  ): AckingQueueJobFactory[F, AckableMessage, A] =
    new AckingQueueJobFactory[F, AckableMessage, A](messages, AckableMessage[F, A])

  def ackingResource[F[_]: ConcurrentEffect, A: JobDecoder](
      messages: Queue[F, Resource[F, A]]
  ): AckingQueueJobFactory[F, Resource, A] =
    new AckingQueueJobFactory[F, Resource, A](messages, messageConverterResource)

  private def messageConverterResource[F[_]: Sync, A](message: A, acker: MessageAcker[F, A]): Resource[F, A] =
    Resource.applyCase[F, A] {
      Sync[F].delay {
        val cleanup: ExitCase[Throwable] => Either[Throwable, Unit] = {
          case ExitCase.Completed        => ().asRight
          case ExitCase.Canceled         => (new CancellationException).asLeft
          case ExitCase.Error(exception) => exception.asLeft
        }
        (message, exitCase => acker.complete(cleanup(exitCase)))
      }
    }
}
