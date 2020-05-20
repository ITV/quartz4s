package com.itv.scheduler

import java.time.Instant
import java.util.Date

import cats.effect._
import cats.implicits._
import fs2.concurrent.Queue
import org.quartz.CronScheduleBuilder._
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz._
import org.quartz.impl.StdSchedulerFactory

import scala.jdk.CollectionConverters._

trait TaskScheduler[F[_], J] {
  def scheduleJob(jobKey: JobKey, job: J, triggerKey: TriggerKey, jobTimeSchedule: JobTimeSchedule): F[Option[Instant]]
}

trait ScheduledMessageReceiver[F[_], A] {
  def messages: Queue[F, A]
}

class QuartzTaskScheduler[F[_], J](
    blocker: Blocker,
    scheduler: Scheduler
)(implicit F: Sync[F], CS: ContextShift[F], jobDataEncoder: JobDataEncoder[J])
    extends TaskScheduler[F, J] {

  override def scheduleJob(
      jobKey: JobKey,
      job: J,
      triggerKey: TriggerKey,
      jobTimeSchedule: JobTimeSchedule
  ): F[Option[Instant]] =
    createJobDetail(jobKey, job) >>= { jobDetail =>
      addJobToScheduler(jobDetail) *>
        addTriggerToScheduler(jobDetail, triggerKey, jobTimeSchedule)
    }

  private def createJobDetail(jobKey: JobKey, job: J): F[JobDetail] =
    F.delay {
      val jobData: JobData = jobDataEncoder(job)
      newJob(classOf[PublishCallbackJob])
        .withIdentity(jobKey)
        .usingJobData(new JobDataMap(jobData.dataMap.asJava))
        .requestRecovery()
        .storeDurably()
        .build
    }

  private def addJobToScheduler(jobDetail: JobDetail): F[Unit] =
    blocker.delay {
      println(s"Adding job: $jobDetail")
      scheduler.addJob(jobDetail, true)
    }

  private def addTriggerToScheduler(
      jobDetail: JobDetail,
      triggerKey: TriggerKey,
      jobTimeSchedule: JobTimeSchedule
  ): F[Option[Instant]] =
    blocker.delay {
      val triggerUpdate: TriggerBuilder[Trigger] => TriggerBuilder[_ <: Trigger] = jobTimeSchedule match {
        case CronScheduledJob(cronExpression) => _.withSchedule(cronSchedule(cronExpression))
        case JobScheduledAt(runTime)          => _.startAt(Date.from(runTime))
      }
      val trigger = triggerUpdate(newTrigger().withIdentity(triggerKey).forJob(jobDetail)).build()
      Option(scheduler.rescheduleJob(triggerKey, trigger))
        .orElse(Option(scheduler.scheduleJob(trigger)))
        .map(_.toInstant)
    }
}

object QuartzTaskScheduler {
  def apply[F[_]: ContextShift, J: JobDataEncoder, A: JobDecoder](
      blocker: Blocker,
      quartzProps: QuartzProperties,
  )(implicit F: ConcurrentEffect[F]): Resource[F, TaskScheduler[F, J] with ScheduledMessageReceiver[F, A]] =
    Resource[F, TaskScheduler[F, J] with ScheduledMessageReceiver[F, A]]((for {
      messages  <- Queue.unbounded[F, A]
      scheduler <- createScheduler(quartzProps, messages)
      _         <- F.delay(scheduler.start())
    } yield (scheduler, messages)).map {
      case (scheduler, messageQueue) =>
        val quartzTaskScheduler: TaskScheduler[F, J] with ScheduledMessageReceiver[F, A] =
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
