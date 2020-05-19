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
import org.quartz.impl.StdSchedulerFactory._

import scala.jdk.CollectionConverters._

trait TaskScheduler[F[_], J] {
  def scheduleJob(job: J, triggerKey: TriggerKey, jobTimeSchedule: JobTimeSchedule): F[Option[Instant]]
}

trait ScheduledMessageReceiver[F[_], A] {
  def messages: Queue[F, A]
}

class QuartzTaskScheduler[F[_], J, A](
    scheduler: Scheduler,
    val messages: Queue[F, A]
)(implicit F: Sync[F], jobDataEncoder: JobDataEncoder[J])
    extends TaskScheduler[F, J]
    with ScheduledMessageReceiver[F, A] {

  override def scheduleJob(
      job: J,
      triggerKey: TriggerKey,
      jobTimeSchedule: JobTimeSchedule
  ): F[Option[Instant]] =
    F.delay {
      val triggerUpdate: TriggerBuilder[Trigger] => TriggerBuilder[_ <: Trigger] = jobTimeSchedule match {
        case CronScheduledJob(cronExpression) => _.withSchedule(cronSchedule(cronExpression))
        case JobScheduledAt(runTime)          => _.startAt(Date.from(runTime))
      }
      val jobData = jobDataEncoder(job)
      val jobDetail =
        newJob(classOf[PublishCallbackJob])
          .withIdentity(jobData.key)
          .usingJobData(new JobDataMap(jobData.dataMap.asJava))
          .requestRecovery()
          .storeDurably()
          .build
      val trigger = triggerUpdate(newTrigger().withIdentity(triggerKey).forJob(jobDetail)).build()
      scheduler.addJob(jobDetail, true)
      Option(scheduler.rescheduleJob(triggerKey, trigger))
        .orElse(Option(scheduler.scheduleJob(trigger)))
        .map(_.toInstant)
    }
}

object QuartzTaskScheduler {
  def apply[F[_], J: JobDataEncoder, A](
      jdbcConfig: JdbcConfig,
      maxConnections: Int,
  )(implicit
      F: ConcurrentEffect[F],
      jobDeserializer: JobDecoder[F, A],
  ): Resource[F, QuartzTaskScheduler[F, J, A]] =
    Resource[F, QuartzTaskScheduler[F, J, A]]((for {
      messages  <- Queue.unbounded[F, A]
      scheduler <- createScheduler(jdbcConfig, maxConnections, messages)
      _         <- F.delay(scheduler.start())
    } yield (scheduler, messages)).map {
      case (scheduler, messages) =>
        val quartzTaskScheduler = new QuartzTaskScheduler[F, J, A](scheduler, messages)
        (quartzTaskScheduler, F.delay(scheduler.shutdown(true)))
    })

  private def createScheduler[F[_], A](
      jdbcConfig: JdbcConfig,
      maxConnections: Int,
      messages: Queue[F, A]
  )(implicit F: ConcurrentEffect[F], jobDeserializer: JobDecoder[F, A]) =
    F.delay {
      val props = new java.util.Properties()
      props.setProperty("org.quartz.jobStore.isClustered", "true")
      props.setProperty(
        "org.quartz.jobStore.driverDelegateClass",
        "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate"
      )
      props.setProperty(PROP_THREAD_POOL_PREFIX + ".threadCount", maxConnections.toString)

      props.setProperty(PROP_JOB_STORE_CLASS, "org.quartz.impl.jdbcjobstore.JobStoreTX")
      val dataSourceName = "ds"
      props.setProperty("org.quartz.jobStore.dataSource", dataSourceName)
      props.setProperty(s"org.quartz.dataSource.$dataSourceName.provider", "hikaricp")
      props.setProperty(s"org.quartz.dataSource.$dataSourceName.driver", jdbcConfig.driverClassName)
      props.setProperty(s"org.quartz.dataSource.$dataSourceName.URL", jdbcConfig.url)
      props.setProperty(s"org.quartz.dataSource.$dataSourceName.user", jdbcConfig.username)
      props.setProperty(s"org.quartz.dataSource.$dataSourceName.password", jdbcConfig.password)
      props.setProperty(s"org.quartz.dataSource.$dataSourceName.maxConnections", maxConnections.toString)

      val sf                   = new StdSchedulerFactory(props)
      val scheduler: Scheduler = sf.getScheduler
      scheduler.setJobFactory(new Fs2StreamJobFactory[F, A](messages))
      scheduler
    }
}
