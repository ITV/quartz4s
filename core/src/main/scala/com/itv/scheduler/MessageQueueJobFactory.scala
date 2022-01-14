package com.itv.scheduler

import cats.MonadThrow
import cats.effect.*
import cats.effect.kernel.Resource.ExitCase
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.all.*
import org.quartz.{Job, Scheduler}
import org.quartz.simpl.PropertySettingJobFactory
import org.quartz.spi.TriggerFiredBundle

import scala.concurrent.CancellationException

trait CallbackJobFactory extends PropertySettingJobFactory {
  def createCallbackJob: PublishCallbackJob

  override def newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job =
    if (classOf[PublishCallbackJob].isAssignableFrom(bundle.getJobDetail.getJobClass)) createCallbackJob
    else super.newJob(bundle, scheduler)
}

trait MessageQueueJobFactory[F[_], A] extends CallbackJobFactory {
  def messages: Queue[F, A]
}

class AutoAckingQueueJobFactory[F[_]: MonadThrow, A: JobDecoder](
    override val messages: Queue[F, A],
    dispatcher: Dispatcher[F]
) extends MessageQueueJobFactory[F, A] {
  override def createCallbackJob: AutoAckCallbackJob[F, A] = new AutoAckCallbackJob[F, A](messages.offer, dispatcher)
}

final case class AckableMessage[F[_], A](message: A, acker: MessageAcker[F])

class AckingQueueJobFactory[F[_]: Concurrent, M[*[_], _], A: JobDecoder](
    override val messages: Queue[F, M[F, A]],
    messageConverter: (A, MessageAcker[F]) => M[F, A],
    dispatcher: Dispatcher[F]
) extends MessageQueueJobFactory[F, M[F, A]] {
  override def createCallbackJob: ExplicitAckCallbackJob[F, A] =
    new ExplicitAckCallbackJob[F, A](
      message =>
        Deferred[F, Either[Throwable, Unit]].flatTap(acker => messages.offer(messageConverter(message, acker))),
      dispatcher
    )
}

object MessageQueueJobFactory {
  def autoAcking[F[_]: MonadThrow, A: JobDecoder](
      messages: Queue[F, A],
      dispatcher: Dispatcher[F]
  ): AutoAckingQueueJobFactory[F, A] =
    new AutoAckingQueueJobFactory[F, A](messages, dispatcher)

  def autoAcking[F[_]: Async, A: JobDecoder](
      messages: Queue[F, A],
  ): Resource[F, AutoAckingQueueJobFactory[F, A]] = Dispatcher[F].map { d =>
    autoAcking(messages, d)
  }

  def acking[F[_]: Concurrent, A: JobDecoder](
      messages: Queue[F, AckableMessage[F, A]],
      dispatcher: Dispatcher[F]
  ): AckingQueueJobFactory[F, AckableMessage, A] =
    new AckingQueueJobFactory[F, AckableMessage, A](messages, AckableMessage[F, A], dispatcher)

  def acking[F[_]: Async, A: JobDecoder](
      messages: Queue[F, AckableMessage[F, A]],
  ): Resource[F, AckingQueueJobFactory[F, AckableMessage, A]] = Dispatcher[F].map { d =>
    acking(messages, d)
  }

  def ackingResource[F[_]: Async, A: JobDecoder](
      messages: Queue[F, Resource[F, A]],
      dispatcher: Dispatcher[F]
  ): AckingQueueJobFactory[F, Resource, A] =
    new AckingQueueJobFactory[F, Resource, A](messages, messageConverterResource, dispatcher)

  def ackingResource[F[_]: Async, A: JobDecoder](
      messages: Queue[F, Resource[F, A]]
  ): Resource[F, AckingQueueJobFactory[F, Resource, A]] = Dispatcher[F].map { d =>
    ackingResource(messages, d)
  }

  private def messageConverterResource[F[_]: Sync, A](message: A, acker: MessageAcker[F]): Resource[F, A] =
    Resource.applyCase[F, A] {
      Sync[F].delay {
        val cleanup: ExitCase => Either[Throwable, Unit] = {
          case ExitCase.Succeeded          => ().asRight
          case ExitCase.Canceled           => (new CancellationException).asLeft
          case ExitCase.Errored(exception) => exception.asLeft
        }
        (message, exitCase => acker.complete(cleanup(exitCase)).void)
      }
    }
}
