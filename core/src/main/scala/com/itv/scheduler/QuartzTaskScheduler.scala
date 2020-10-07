package com.itv.scheduler

import java.time.Instant
import java.util.Date

import cats.effect._
import cats.implicits._
import cats.Apply
import org.quartz.CronScheduleBuilder._
import org.quartz.SimpleScheduleBuilder._
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz._
import org.quartz.impl.StdSchedulerFactory

import scala.collection.JavaConverters._

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

class QuartzTaskScheduler[F[_], J](
    scheduler: Scheduler
)(implicit F: Sync[F], jobDataEncoder: JobDataEncoder[J])
    extends TaskScheduler[F, J] {

  override def createJob(jobKey: JobKey, job: J): F[Unit] =
    F.delay {
      val jobData: JobData = jobDataEncoder(job)
      val jobDetail = newJob(classOf[PublishCallbackJob])
        .withIdentity(jobKey)
        .usingJobData(new JobDataMap(jobData.dataMap.asJava))
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
    F.delay {
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
    F.delay {
      scheduler.deleteJob(jobKey)
    }

  override def pauseTrigger(triggerKey: TriggerKey): F[Unit] =
    F.delay {
      scheduler.pauseTrigger(triggerKey)
    }
}

object QuartzTaskScheduler {
  def apply[F[_], J: JobDataEncoder](
      quartzConfig: Fs2QuartzConfig,
      callbackJobFactory: CallbackJobFactory,
  )(implicit F: Sync[F]): Resource[F, QuartzTaskScheduler[F, J]] =
    apply(quartzConfig.toQuartzProperties, callbackJobFactory)

  def apply[F[_], J: JobDataEncoder](
      quartzProps: QuartzProperties,
      callbackJobFactory: CallbackJobFactory,
  )(implicit F: Sync[F]): Resource[F, QuartzTaskScheduler[F, J]] =
    Resource[F, QuartzTaskScheduler[F, J]](
      createScheduler(quartzProps, callbackJobFactory)
        .flatTap(scheduler => F.delay(scheduler.start()))
        .map { scheduler =>
          val quartzTaskScheduler: QuartzTaskScheduler[F, J] = new QuartzTaskScheduler[F, J](scheduler)
          (quartzTaskScheduler, F.delay(scheduler.shutdown(true)))
        }
    )

  private def createScheduler[F[_]](
      quartzProps: QuartzProperties,
      callbackJobFactory: CallbackJobFactory,
  )(implicit F: Sync[F]): F[Scheduler] =
    F.delay {
      val sf                   = new StdSchedulerFactory(quartzProps.properties)
      val scheduler: Scheduler = sf.getScheduler
      scheduler.setJobFactory(callbackJobFactory)
      scheduler
    }
}
