package com.itv.scheduler

import java.time.Instant
import java.util.Properties
import java.util.concurrent.Executors

import cats.effect._
import cats.implicits._
import com.dimafeng.testcontainers._
import fs2.concurrent.Queue
import org.flywaydb.core.Flyway
import org.quartz.{CronExpression, JobKey, TriggerKey}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class QuartzTaskSchedulerTest extends AnyFlatSpec with Matchers with ForAllTestContainer with BeforeAndAfterEach {
  override val container: PostgreSQLContainer = PostgreSQLContainer()

  implicit val timer: Timer[IO]               = IO.timer(ExecutionContext.global)
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

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

  it should "schedule jobs to run every second" in {
    val blocker           = Blocker.liftExecutorService(Executors.newFixedThreadPool(8))
    val messageQueue      = Queue.unbounded[IO, ParentTestJob].unsafeRunSync()
    val jobFactory        = Fs2StreamJobFactory.autoAcking[IO, ParentTestJob](messageQueue)
    val schedulerResource = QuartzTaskScheduler[IO, ParentTestJob](blocker, quartzProperties, jobFactory)

    val elementCount = 6
    val userJob      = UserJob("user-id-123")

    val messages = schedulerResource
      .use { scheduler =>
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
          messageQueue.dequeue.take(elementCount).compile.toList
      }
      .unsafeRunTimed(10.seconds)
      .getOrElse(fail("Operation timed out completing"))

    val expectedMessages: List[ParentTestJob] = List.fill[ParentTestJob](elementCount - 1)(ChildObjectJob) :+ userJob
    messages should contain theSameElementsAs expectedMessages
  }
}
