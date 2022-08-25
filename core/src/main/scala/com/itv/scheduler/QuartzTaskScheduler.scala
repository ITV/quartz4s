package com.itv.scheduler

import java.time.Instant
import java.util.Date
import cats.effect.*
import cats.syntax.all.*
import cats.Apply
import com.itv.scheduler.QuartzOps.*
import org.quartz.CronScheduleBuilder.*
import org.quartz.SimpleScheduleBuilder.*
import org.quartz.JobBuilder.*
import org.quartz.TriggerBuilder.*
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.matchers.GroupMatcher

trait TaskScheduler[F[_], J] {
  def scheduleJob(
      jobKey: JobKey,
      job: J,
      triggerKey: TriggerKey,
      jobTimeSchedule: JobTimeSchedule
  )(implicit F: Apply[F]): F[Option[Instant]] =
    createJob(jobKey, job) *>
      scheduleTrigger(jobKey, triggerKey, jobTimeSchedule)

  def checkExists(jobKey: JobKey): F[Boolean]

  def checkExists(triggerKey: TriggerKey): F[Boolean]

  def createJob(jobKey: JobKey, job: J): F[Unit]

  def scheduleTrigger(jobKey: JobKey, triggerKey: TriggerKey, jobTimeSchedule: JobTimeSchedule): F[Option[Instant]]

  def deleteJob(jobKey: JobKey): F[Unit]

  def pauseTrigger(triggerKey: TriggerKey): F[Unit]

  def getJobKeys(matcher: GroupMatcher[JobKey]): F[List[JobKey]]

  def rescheduleJob(triggerKey: TriggerKey, trigger: Trigger): F[Unit]
}

class QuartzTaskScheduler[F[_], J](
    scheduler: Scheduler
)(implicit F: Sync[F], jobDataEncoder: JobDataEncoder[J])
    extends TaskScheduler[F, J] {

  override def checkExists(jobKey: JobKey): F[Boolean]         = F.blocking(scheduler.checkExists(jobKey))
  override def checkExists(triggerKey: TriggerKey): F[Boolean] = F.blocking(scheduler.checkExists(triggerKey))

  override def createJob(jobKey: JobKey, job: J): F[Unit] =
    F.blocking {
      val jobData: JobData = jobDataEncoder.encode(job)
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
    F.blocking {
      val triggerUpdate: TriggerBuilder[Trigger] => TriggerBuilder[? <: Trigger] = jobTimeSchedule match {
        case CronScheduledJob(cronExpression) => _.withSchedule(cronSchedule(cronExpression))
        case JobScheduledAt(runTime)          => _.startAt(Date.from(runTime))
        case SimpleJob(repeatEvery) => _.withSchedule(repeatSecondlyForever(repeatEvery.toSeconds.toInt.max(1)))
      }
      val trigger = triggerUpdate(newTrigger().withIdentity(triggerKey).forJob(jobKey)).build()
      Option(scheduler.rescheduleJob(triggerKey, trigger))
        .orElse(Option(scheduler.scheduleJob(trigger)))
        .map(_.toInstant)
    }

  override def deleteJob(jobKey: JobKey): F[Unit] =
    F.blocking {
      scheduler.deleteJob(jobKey)
    }.void

  override def pauseTrigger(triggerKey: TriggerKey): F[Unit] =
    F.blocking {
      scheduler.pauseTrigger(triggerKey)
    }

  override def rescheduleJob(triggerKey: TriggerKey, trigger: Trigger): F[Unit] =
    F.blocking {
      scheduler.rescheduleJob(triggerKey, trigger)
    }

  override def getJobKeys(matcher: GroupMatcher[JobKey]): F[List[JobKey]] =
    F.blocking {
      scheduler.getJobKeys(matcher).toList
    }
}

object QuartzTaskScheduler {
  def apply[F[_], J: JobDataEncoder](
      quartzConfig: Quartz4sConfig,
      callbackJobFactory: CallbackJobFactory,
  )(implicit F: Sync[F]): Resource[F, QuartzTaskScheduler[F, J]] =
    apply(quartzConfig.toQuartzProperties, callbackJobFactory)

  def apply[F[_], J: JobDataEncoder](
      quartzProps: QuartzProperties,
      callbackJobFactory: CallbackJobFactory
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
