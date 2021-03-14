package com.itv.scheduler

import cats.MonadError
import cats.effect._
import cats.effect.kernel.Deferred
import cats.effect.kernel.Resource.ExitCase
import cats.effect.std.{Dispatcher, Queue}
import cats.syntax.all._
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

class AutoAckingQueueJobFactory[F[_]: MonadError[*[_], Throwable], A: JobDecoder](
    dispatcher: Dispatcher[F],
    override val messages: Queue[F, A]
) extends MessageQueueJobFactory[F, A] {
  override def createCallbackJob: AutoAckCallbackJob[F, A] = new AutoAckCallbackJob[F, A](dispatcher, messages.offer)
}

final case class AckableMessage[F[_], A](message: A, acker: MessageAcker[F])

class AckingQueueJobFactory[F[_]: Concurrent, M[*[_], _], A: JobDecoder](
    dispatcher: Dispatcher[F],
    override val messages: Queue[F, M[F, A]],
    messageConverter: (A, Deferred[F, Either[Throwable, Unit]]) => M[F, A]
) extends MessageQueueJobFactory[F, M[F, A]] {
  override def createCallbackJob: ExplicitAckCallbackJob[F, A] =
    new ExplicitAckCallbackJob[F, A](
      dispatcher,
      message => Deferred[F, Either[Throwable, Unit]].flatTap(acker => messages.offer(messageConverter(message, acker)))
    )
}

object Fs2StreamJobFactory {
  def autoAcking[F[_]: MonadError[*[_], Throwable], A: JobDecoder](
      dispatcher: Dispatcher[F],
      messages: Queue[F, A]
  ): AutoAckingQueueJobFactory[F, A] =
    new AutoAckingQueueJobFactory[F, A](dispatcher, messages)

  def acking[F[_]: Concurrent, A: JobDecoder](
      dispatcher: Dispatcher[F],
      messages: Queue[F, AckableMessage[F, A]]
  ): AckingQueueJobFactory[F, AckableMessage, A] =
    new AckingQueueJobFactory[F, AckableMessage, A](dispatcher, messages, AckableMessage[F, A])

  def ackingResource[F[_]: Concurrent: Sync: Dispatcher, A: JobDecoder](
      dispatcher: Dispatcher[F],
      messages: Queue[F, Resource[F, A]]
  ): AckingQueueJobFactory[F, Resource, A] =
    new AckingQueueJobFactory[F, Resource, A](dispatcher, messages, messageConverterResource)

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
