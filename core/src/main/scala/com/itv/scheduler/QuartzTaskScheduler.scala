package com.itv.scheduler

import java.time.Instant
import java.util.Date

import cats.effect._
import cats.implicits._
import cats.Apply
import fs2.concurrent.Queue
import org.quartz.CronScheduleBuilder._
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

trait ScheduledMessageReceiver[F[_], A] {
  def messages: Queue[F, A]
}

class QuartzTaskScheduler[F[_], J](
    blocker: Blocker,
    scheduler: Scheduler
)(implicit F: Sync[F], CS: ContextShift[F], jobDataEncoder: JobDataEncoder[J])
    extends TaskScheduler[F, J] {

  override def createJob(jobKey: JobKey, job: J): F[Unit] =
    blocker.delay {
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
    blocker.delay {
      val triggerUpdate: TriggerBuilder[Trigger] => TriggerBuilder[_ <: Trigger] = jobTimeSchedule match {
        case CronScheduledJob(cronExpression) => _.withSchedule(cronSchedule(cronExpression))
        case JobScheduledAt(runTime)          => _.startAt(Date.from(runTime))
      }
      val trigger = triggerUpdate(newTrigger().withIdentity(triggerKey).forJob(jobKey)).build()
      Option(scheduler.rescheduleJob(triggerKey, trigger))
        .orElse(Option(scheduler.scheduleJob(trigger)))
        .map(_.toInstant)
    }

  override def deleteJob(jobKey: JobKey): F[Unit] =
    blocker.delay {
      scheduler.deleteJob(jobKey)
    }

  override def pauseTrigger(triggerKey: TriggerKey): F[Unit] =
    blocker.delay {
      scheduler.pauseTrigger(triggerKey)
    }
}

object QuartzTaskScheduler {
  def apply[F[_]: ContextShift, J: JobDataEncoder, A: JobDecoder](
      blocker: Blocker,
      quartzConfig: Fs2QuartzConfig,
  )(implicit F: ConcurrentEffect[F]): Resource[F, MessageScheduler[F, J, A]] =
    apply(blocker, quartzConfig.toQuartzProperties)

  def apply[F[_]: ContextShift, J: JobDataEncoder, A: JobDecoder](
      blocker: Blocker,
      quartzProps: QuartzProperties,
  )(implicit F: ConcurrentEffect[F]): Resource[F, MessageScheduler[F, J, A]] =
    Resource[F, MessageScheduler[F, J, A]]((for {
      messages  <- Queue.unbounded[F, A]
      scheduler <- createScheduler(quartzProps, messages)
      _         <- F.delay(scheduler.start())
    } yield (scheduler, messages)).map {
      case (scheduler, messageQueue) =>
        val quartzTaskScheduler: MessageScheduler[F, J, A] =
          new QuartzTaskScheduler[F, J](blocker, scheduler) with ScheduledMessageReceiver[F, A] {
            override val messages: Queue[F, A] = messageQueue
          }
        (quartzTaskScheduler, F.delay(scheduler.shutdown(true)))
    })

  private def createScheduler[F[_], A: JobDecoder](
      quartzProps: QuartzProperties,
      messages: Queue[F, A]
  )(implicit F: ConcurrentEffect[F]): F[Scheduler] =
    F.delay {
      val sf                   = new StdSchedulerFactory(quartzProps.properties)
      val scheduler: Scheduler = sf.getScheduler
      scheduler.setJobFactory(new Fs2StreamJobFactory[F, A](messages))
      scheduler
    }
}
