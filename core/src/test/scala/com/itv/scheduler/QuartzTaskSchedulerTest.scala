package com.itv.scheduler

import java.time.Instant
import java.util.Properties

import cats.effect._
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.dimafeng.testcontainers._
import cats.effect.std.Queue
import org.flywaydb.core.Flyway
import org.quartz.{CronExpression, JobKey, TriggerKey}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class QuartzTaskSchedulerTest extends AnyFlatSpec with Matchers with ForAllTestContainer with BeforeAndAfterEach {
  override val container: PostgreSQLContainer = PostgreSQLContainer()

  lazy val quartzProperties: QuartzProperties = {
    val props = new Properties()
    props.setProperty("org.quartz.jobStore.isClustered", "true")
    props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate")
    props.setProperty("org.quartz.threadPool.threadCount", "4")
    props.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX")
    props.setProperty("org.quartz.jobStore.dataSource", "ds")
    props.setProperty("org.quartz.dataSource.ds.provider", "hikaricp")
    props.setProperty("org.quartz.dataSource.ds.driver", container.driverClassName)
    props.setProperty("org.quartz.dataSource.ds.URL", container.jdbcUrl)
    props.setProperty("org.quartz.dataSource.ds.user", container.username)
    props.setProperty("org.quartz.dataSource.ds.password", container.password)
    props.setProperty("org.quartz.dataSource.ds.maxConnections", "4")
    QuartzProperties(props)
  }

  lazy val flyway: Flyway =
    Flyway.configure().dataSource(container.jdbcUrl, container.username, container.password).load()

  override def beforeEach(): Unit = {
    println("Migrating db ...")
    flyway.migrate()
    println("Migrated db")
  }

  behavior of "QuartzTaskScheduler"

  def schedulerResource(
      messageQueue: Queue[IO, ParentTestJob]
  ): Resource[IO, QuartzTaskScheduler[IO, ParentTestJob]] =
    Dispatcher[IO].flatMap { dispatcher =>
      val jobFactory = Fs2StreamJobFactory.autoAcking[IO, ParentTestJob](dispatcher, messageQueue)
      QuartzTaskScheduler[IO, ParentTestJob](quartzProperties, jobFactory)
    }

  it should "schedule jobs to run every second" in {
    val elementCount = 6
    val userJob      = UserJob("user-id-123")

    val result = Queue.unbounded[IO, ParentTestJob] >>= { messageQueue =>
      schedulerResource(messageQueue).use { scheduler =>
        scheduler
          .scheduleJob(
            JobKey.jobKey("child-object-job"),
            ChildObjectJob,
            TriggerKey.triggerKey("cron-test-trigger"),
            CronScheduledJob(new CronExpression("* * * ? * *"))
          )
          .flatTap(runTime => IO(println(s"Next cron job scheduled for $runTime"))) *>
          scheduler
            .scheduleJob(
              JobKey.jobKey("single-user-job"),
              userJob,
              TriggerKey.triggerKey("scheduled-single-test-trigger"),
              JobScheduledAt(Instant.now.plusSeconds(2))
            )
            .flatTap(runTime => IO(println(s"Next single job scheduled for $runTime"))) *>
          messageQueue.take.replicateA(elementCount)
      }
    }
    val messages = result.unsafeRunTimed(5.seconds).getOrElse(fail("Operation timed out completing"))

    val expectedMessages: List[ParentTestJob] =
      List.fill[ParentTestJob](elementCount - 1)(ChildObjectJob) :+ userJob
    messages should contain theSameElementsAs expectedMessages
  }
}
