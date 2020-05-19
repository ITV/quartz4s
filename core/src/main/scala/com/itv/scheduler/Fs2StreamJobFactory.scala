package com.itv.scheduler

import cats.effect._
import cats.implicits._
import fs2.concurrent.Queue
import org.quartz.{Job, JobExecutionContext, Scheduler}
import org.quartz.simpl.PropertySettingJobFactory
import org.quartz.spi.TriggerFiredBundle

import scala.util.Either

class Fs2StreamJobFactory[F[_], A](messages: Queue[F, A])(implicit F: ConcurrentEffect[F], jobDecoder: JobDecoder[A])
    extends PropertySettingJobFactory {
  override def newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job =
    if (classOf[PublishCallbackJob].isAssignableFrom(bundle.getJobDetail.getJobClass))
      new PublishCallbackJob {
        override def handleMessage: JobExecutionContext => Either[Throwable, Unit] =
          jobExecutionContext =>
            F.toIO(jobDecoder(jobExecutionContext).liftTo[F] >>= messages.enqueue1).attempt.unsafeRunSync()
      }
    else
      super.newJob(bundle, scheduler)
}
