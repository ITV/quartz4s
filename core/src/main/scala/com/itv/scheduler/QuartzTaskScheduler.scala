package com.itv.scheduler

import java.time.Instant
import java.util.Date

import cats.effect._
import cats.syntax.all._
import cats.Apply
import cats.data.Kleisli
import com.itv.scheduler.QuartzOps._
import org.quartz.CronScheduleBuilder._
import org.quartz.SimpleScheduleBuilder._
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz._
import org.quartz.impl.StdSchedulerFactory

trait TaskScheduler[F[_], J] {
  def scheduleJob(
      jobKey: JobKey,
      job: J,
      triggerKey: TriggerKey,
      jobTimeSchedule: JobTimeSchedule
  )(implicit F: Apply[F]): F[Option[Instant]] =
    createJob(jobKey, job) *>
      scheduleTrigger(jobKey, triggerKey, jobTimeSchedule)

  def createJob(jobKey: JobKey, job: J): F[Unit]

  def scheduleTrigger(jobKey: JobKey, triggerKey: TriggerKey, jobTimeSchedule: JobTimeSchedule): F[Option[Instant]]

  def deleteJob(jobKey: JobKey): F[Unit]

  def pauseTrigger(triggerKey: TriggerKey): F[Unit]
}

object TaskScheduler {
  implicit def deriveKleisli[F[_], J, A](taskScheduler: TaskScheduler[F, J]): TaskScheduler[Kleisli[F, A, *], J] =
    new TaskScheduler[Kleisli[F, A, *], J] {
      def createJob(jobKey: JobKey, job: J): Kleisli[F, A, Unit] =
        Kleisli.liftK(taskScheduler.createJob(jobKey, job))
      def scheduleTrigger(jobKey: JobKey, triggerKey: TriggerKey, jobTimeSchedule: JobTimeSchedule): Kleisli[F, A, Option[Instant]] =
        Kleisli.liftK(taskScheduler.scheduleTrigger(jobKey, triggerKey, jobTimeSchedule))
      def deleteJob(jobKey: JobKey): Kleisli[F, A, Unit] =
        Kleisli.liftK(taskScheduler.deleteJob(jobKey))
      def pauseTrigger(triggerKey: TriggerKey): Kleisli[F, A, Unit] =
        Kleisli.liftK(taskScheduler.pauseTrigger(triggerKey))
    }
}

class QuartzTaskScheduler[F[_], J](
    val blocker: Blocker,
    val scheduler: Scheduler
)(implicit F: Sync[F], CS: ContextShift[F], jobDataEncoder: JobDataEncoder[J])
    extends TaskScheduler[F, J] {

  override def createJob(jobKey: JobKey, job: J): F[Unit] =
    blocker.delay {
      val jobData: JobData = jobDataEncoder(job)
      val jobDetail = newJob(classOf[PublishCallbackJob])
        .withIdentity(jobKey)
        .usingJobData(jobData.toJobDataMap)
        .requestRecovery()
        .storeDurably()
        .build
      scheduler.addJob(jobDetail, true)
    }

  override def scheduleTrigger(
      jobKey: JobKey,
      triggerKey: TriggerKey,
      jobTimeSchedule: JobTimeSchedule
  ): F[Option[Instant]] =
    blocker.delay {
      val triggerUpdate: TriggerBuilder[Trigger] => TriggerBuilder[_ <: Trigger] = jobTimeSchedule match {
        case CronScheduledJob(cronExpression) => _.withSchedule(cronSchedule(cronExpression))
        case JobScheduledAt(runTime)          => _.startAt(Date.from(runTime))
        case SimpleJob(repeatEvery)           => _.withSchedule(repeatSecondlyForever(repeatEvery.toSeconds.toInt.max(1)))
      }
      val trigger = triggerUpdate(newTrigger().withIdentity(triggerKey).forJob(jobKey)).build()
      Option(scheduler.rescheduleJob(triggerKey, trigger))
        .orElse(Option(scheduler.scheduleJob(trigger)))
        .map(_.toInstant)
    }

  override def deleteJob(jobKey: JobKey): F[Unit] =
    blocker.delay {
      scheduler.deleteJob(jobKey)
    }.void

  override def pauseTrigger(triggerKey: TriggerKey): F[Unit] =
    blocker.delay {
      scheduler.pauseTrigger(triggerKey)
    }
}

object QuartzTaskScheduler {
  def apply[F[_]: ContextShift, J: JobDataEncoder](
      blocker: Blocker,
      quartzConfig: Fs2QuartzConfig,
      callbackJobFactory: CallbackJobFactory,
  )(implicit F: ConcurrentEffect[F]): Resource[F, QuartzTaskScheduler[F, J]] =
    apply(blocker, quartzConfig.toQuartzProperties, callbackJobFactory)

  def apply[F[_]: ContextShift, J: JobDataEncoder](
      blocker: Blocker,
      quartzProps: QuartzProperties,
      callbackJobFactory: CallbackJobFactory,
  )(implicit F: ConcurrentEffect[F]): Resource[F, QuartzTaskScheduler[F, J]] =
    Resource[F, QuartzTaskScheduler[F, J]](
      createScheduler(quartzProps, callbackJobFactory)
        .flatTap(scheduler => F.delay(scheduler.start()))
        .map { scheduler =>
          val quartzTaskScheduler: QuartzTaskScheduler[F, J] = new QuartzTaskScheduler[F, J](blocker, scheduler)
          (quartzTaskScheduler, F.delay(scheduler.shutdown(true)))
        }
    )

  private def createScheduler[F[_]](
      quartzProps: QuartzProperties,
      callbackJobFactory: CallbackJobFactory,
  )(implicit F: ConcurrentEffect[F]): F[Scheduler] =
    F.delay {
      val sf = new StdSchedulerFactory(quartzProps.properties)
      val scheduler: Scheduler = sf.getScheduler
      scheduler.setJobFactory(callbackJobFactory)
      scheduler
    }
}
