package com.itv.scheduler

import cats.effect.*
import cats.effect.std.Queue
import cats.effect.unsafe.IORuntime
import cats.syntax.all.*
import com.dimafeng.testcontainers.*
import org.flywaydb.core.Flyway
import org.quartz.{CronExpression, JobKey, TriggerKey}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName

import java.time.Instant
import java.util.Properties
import scala.concurrent.duration.*

class QuartzTaskSchedulerTest extends AnyFlatSpec with Matchers with ForAllTestContainer with BeforeAndAfterEach {
  override val container: PostgreSQLContainer =
    PostgreSQLContainer(dockerImageNameOverride = DockerImageName.parse("postgres:14.1"))

  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

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
    MessageQueueJobFactory.autoAcking[IO, ParentTestJob](messageQueue).flatMap { jobFactory =>
      QuartzTaskScheduler[IO, ParentTestJob](quartzProperties, jobFactory)
    }

  it should "support job idempotency checks" in {
    (for {
      queue <- Queue.unbounded[IO, ParentTestJob]
      _ <- schedulerResource(messageQueue = queue).use { scheduler =>
        for {
          _ <- scheduler.scheduleJob(
            JobKey.jobKey("sample-job"),
            ChildObjectJob,
            TriggerKey.triggerKey("sample-trigger"),
            JobScheduledAt(Instant.now().plusSeconds(2))
          )

          exists      <- scheduler.checkExists(JobKey.jobKey("sample-job"))
          nonexistent <- scheduler.checkExists(JobKey.jobKey("non-job"))

        } yield {
          exists shouldBe true
          nonexistent shouldBe false
        }
      }
    } yield ())
      .timeout(5.seconds)
      .unsafeRunSync()
  }

  it should "support trigger idempotency checks" in {
    (for {
      queue <- Queue.unbounded[IO, ParentTestJob]
      _ <- schedulerResource(messageQueue = queue).use { scheduler =>
        for {
          _ <- scheduler.scheduleJob(
            JobKey.jobKey("sample-job"),
            ChildObjectJob,
            TriggerKey.triggerKey("sample-trigger"),
            JobScheduledAt(Instant.now().plusSeconds(2))
          )

          exists      <- scheduler.checkExists(TriggerKey.triggerKey("sample-trigger"))
          nonexistent <- scheduler.checkExists(TriggerKey.triggerKey("non-trigger"))

        } yield {
          exists shouldBe true
          nonexistent shouldBe false
        }
      }
    } yield ())
      .timeout(5.seconds)
      .unsafeRunSync()
  }

  it should "schedule jobs to run every second" in {
    val elementCount = 6
    val userJob      = UserJob(UserId("user-id-123"))

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
    val messages = result.unsafeRunTimed(10.seconds).getOrElse(fail("Operation timed out completing"))

    val expectedMessages: List[ParentTestJob] = List.fill[ParentTestJob](elementCount - 1)(ChildObjectJob) :+ userJob
    messages should contain theSameElementsAs expectedMessages
  }
}
