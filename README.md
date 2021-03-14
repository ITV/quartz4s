# fs2-quartz
Quarts scheduler library using fs2

### Import
```scala
libraryDependencies ++= Seq(
  "com.itv" %% "fs2-quartz-core"     % "0.7.0",
  "com.itv" %% "fs2-quartz-extruder" % "0.7.0"
)
```

The project uses a quartz scheduler, and as scheduled messages are generated from Quartz they are
decoded and put onto an `cats.effect.std.Queue`.

#### Components for scheduling jobs:
* a `QuartzTaskScheduler[F[_], A]` which schedules jobs of type `A`
* a `JobDataEncoder[A]` which encodes job data in a map for the given job of type `A`

#### Components for responding to scheduled messages:
* a job factory which is triggered by quartz when a scheduled task occurs and creates messages to put on the queue
* a `JobDecoder[A]` which decodes the incoming message data map into an `A`
* the decoded message is put onto the provided `cats.effect.std.Queue`


## Usage:

## Create some job types
We need to have a set of types to encode and decode.
The [extruder](https://janstenpickle.github.io/extruder/) project provides the ability to
encode/decode an object as a `Map[String, String]`, which works perfectly for 
putting data into the quartz `JobDataMap`.
```scala
import com.itv.scheduler.{JobDataEncoder, JobDecoder}
import com.itv.scheduler.extruder.implicits._
import extruder.core._
import extruder.map._

sealed trait ParentJob
case object ChildObjectJob     extends ParentJob
case class UserJob(id: String) extends ParentJob

object ParentJob {
  implicit val jobDataEncoder: JobDataEncoder[ParentJob] = deriveEncoder[ParentJob]
  implicit val jobDecoder: JobDecoder[ParentJob]         = deriveDecoder[ParentJob]
}
```

### Create a JobFactory
There are 2 options when creating a `CallbackJobFactory`: auto-acked and manually acked messages.

#### Auto-Acked messages
Scheduled jobs from quartz are immediately acked and the resulting message of type `A` is placed on a `Queue[F, A]`.
If the message taken from the queue isn't handled cleanly then the resulting quartz job won't be re-run,
as it has already been marked as successful. 
```scala
import cats.effect._
import com.itv.scheduler._
import cats.effect.std.Queue
import scala.concurrent.ExecutionContext

implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
// contextShift: ContextShift[IO] = cats.effect.internals.IOContextShift@738c87ec

val jobMessageQueue = Queue.unbounded[IO, ParentJob].unsafeRunSync()
// jobMessageQueue: Queue[IO, ParentJob] = fs2.concurrent.Queue$InPartiallyApplied$$anon$3@61313295
val autoAckJobFactory = Fs2StreamJobFactory.autoAcking[IO, ParentJob](jobMessageQueue)
// autoAckJobFactory: AutoAckingQueueJobFactory[IO, ParentJob] = com.itv.scheduler.AutoAckingQueueJobFactory@1663393b
```

#### Manually Acked messages
Scheduled jobs are received but only acked with quartz once the handler has completed via an `acker: MessageAcker[F]`.

Scheduled jobs from quartz are bundled into a `message: A` and an `acker: MessageAcker[F]`.
The items in the queue are each a `Resource[F, A]` which uses the message and acks the message as the `Resource` is `use`d.

Alternatively the lower-level way of handling each message is via a queue of
`AckableMessage[F, A](message: A, acker: MessageAcker[F])` items where the message is explicitly acked by the user.

In both cases, the quartz job is only marked as complete once the `acker.complete(result: Either[Throwable, Unit])` is called.
```scala
// each message is wrapped as a `Resource` which acks on completion
val ackableJobResourceMessageQueue = Queue.unbounded[IO, Resource[IO, ParentJob]].unsafeRunSync()
// ackableJobResourceMessageQueue: Queue[IO, Resource[IO, ParentJob]] = fs2.concurrent.Queue$InPartiallyApplied$$anon$3@b21463
val ackingResourceJobFactory: AckingQueueJobFactory[IO, Resource, ParentJob] =
  Fs2StreamJobFactory.ackingResource(ackableJobResourceMessageQueue)
// ackingResourceJobFactory: AckingQueueJobFactory[IO, Resource, ParentJob] = com.itv.scheduler.AckingQueueJobFactory@279c4c65

// each message is wrapped as a `AckableMessage` which acks on completion
val ackableJobMessageQueue = Queue.unbounded[IO, AckableMessage[IO, ParentJob]].unsafeRunSync()
// ackableJobMessageQueue: Queue[IO, AckableMessage[IO, ParentJob]] = fs2.concurrent.Queue$InPartiallyApplied$$anon$3@4f516799
val ackingJobFactory: AckingQueueJobFactory[IO, AckableMessage, ParentJob] =
  Fs2StreamJobFactory.acking(ackableJobMessageQueue)
// ackingJobFactory: AckingQueueJobFactory[IO, AckableMessage, ParentJob] = com.itv.scheduler.AckingQueueJobFactory@40f435a9
```

### Creating a scheduler
```scala
import java.util.concurrent.Executors
import com.itv.scheduler.extruder.implicits._

val quartzProperties = QuartzProperties(new java.util.Properties())
// quartzProperties: QuartzProperties = QuartzProperties({})
val blocker = Blocker.liftExecutorService(Executors.newFixedThreadPool(8))
// blocker: Blocker = cats.effect.Blocker@40b1d8d6
val schedulerResource: Resource[IO, QuartzTaskScheduler[IO, ParentJob]] =
  QuartzTaskScheduler[IO, ParentJob](blocker, quartzProperties, autoAckJobFactory)
// schedulerResource: Resource[IO, QuartzTaskScheduler[IO, ParentJob]] = Allocate(
//   Map(
//     Bind(
//       Delay(
//         com.itv.scheduler.QuartzTaskScheduler$$$Lambda$5407/428267345@604d5061
//       ),
//       cats.FlatMap$$Lambda$5409/1722445077@42cf346
//     ),
//     scala.Function1$$Lambda$5397/275794287@3958172e,
//     1
//   )
// )
```

### Using the scheduler
```scala
import java.time.Instant
import org.quartz.{CronExpression, JobKey, TriggerKey}

def scheduleCronJob(scheduler: QuartzTaskScheduler[IO, ParentJob]): IO[Option[Instant]] =
  scheduler.scheduleJob(
    JobKey.jobKey("child-object-job"),
    ChildObjectJob,
    TriggerKey.triggerKey("cron-test-trigger"),
    CronScheduledJob(new CronExpression("* * * ? * *"))
  )

def scheduleSingleJob(scheduler: QuartzTaskScheduler[IO, ParentJob]): IO[Option[Instant]] =
  scheduler.scheduleJob(
    JobKey.jobKey("single-user-job"),
    UserJob("user-123"),
    TriggerKey.triggerKey("scheduled-single-test-trigger"),
    JobScheduledAt(Instant.now.plusSeconds(2))
  )
```
