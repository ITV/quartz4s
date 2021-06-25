# fs2-quartz
Quarts scheduler library using fs2

### Import
```scala
libraryDependencies ++= Seq(
  "com.itv" %% "fs2-quartz-core"     % "0.8.3-SNAPSHOT",
  "com.itv" %% "fs2-quartz-extruder" % "0.8.3-SNAPSHOT"
)
```

The project uses a quartz scheduler, and as scheduled messages are generated from Quartz they are
decoded and put onto an `fs2.concurrent.Queue`.

#### Components for scheduling jobs:
* a `QuartzTaskScheduler[F[_], A]` which schedules jobs of type `A`
* a `JobDataEncoder[A]` which encodes job data in a map for the given job of type `A`

#### Components for responding to scheduled messages:
* a job factory which is triggered by quartz when a scheduled task occurs and creates messages to put on the queue
* a `JobDecoder[A]` which decodes the incoming message data map into an `A`
* the decoded message is put onto the provided `fs2.concurrent.Queue`


## Usage:

## Create some job types
We need to have a set of types to encode and decode.
The [extruder](https://janstenpickle.github.io/extruder/) project provides the ability to
encode/decode an object as a `Map[String, String]`, which works perfectly for 
putting data into the quartz `JobDataMap`.
```scala
import com.itv.scheduler.{JobDataEncoder, JobDecoder}
import com.itv.scheduler.extruder.implicits._
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
import cats.effect.unsafe.implicits.global

val jobMessageQueue = Queue.unbounded[IO, ParentJob].unsafeRunSync()
// jobMessageQueue: Queue[IO, ParentJob] = cats.effect.std.Queue$BoundedQueue@319a521d
val autoAckJobFactory = Fs2StreamJobFactory.autoAcking[IO, ParentJob](jobMessageQueue)
// autoAckJobFactory: Resource[IO, AutoAckingQueueJobFactory[IO, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$12665/0x00000008046fb840@32bd730e
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$12667/0x0000000804710840@49f2493b
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$12668/0x0000000804711040@73b2e615
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$12667/0x0000000804710840@3972038a
// )
```

#### Manually Acked messages
Scheduled jobs are received but only acked with quartz once the handler has completed via an `acker: MessageAcker[F, A]`.

Scheduled jobs from quartz are bundled into a `message: A` and an `acker: MessageAcker[F, A]`.
The items in the queue are each a `Resource[F, A]` which uses the message and acks the message as the `Resource` is `use`d.

Alternatively the lower-level way of handling each message is via a queue of
`AckableMessage[F, A](message: A, acker: MessageAcker[F, A])` items where the message is explicitly acked by the user.

In both cases, the quartz job is only marked as complete once the `acker.complete(result: Either[Throwable, Unit])` is called.
```scala
// each message is wrapped as a `Resource` which acks on completion
val ackableJobResourceMessageQueue = Queue.unbounded[IO, Resource[IO, ParentJob]].unsafeRunSync()
// ackableJobResourceMessageQueue: Queue[IO, Resource[IO, ParentJob]] = cats.effect.std.Queue$BoundedQueue@6dea7a63
val ackingResourceJobFactory: Resource[IO, AckingQueueJobFactory[IO, Resource, ParentJob]] =
  Fs2StreamJobFactory.ackingResource(ackableJobResourceMessageQueue)
// ackingResourceJobFactory: Resource[IO, AckingQueueJobFactory[IO, Resource, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$12665/0x00000008046fb840@1e9033a4
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$12667/0x0000000804710840@20977b77
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$12668/0x0000000804711040@e446010
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$12667/0x0000000804710840@3b8da3ed
// )

// each message is wrapped as a `AckableMessage` which acks on completion
val ackableJobMessageQueue = Queue.unbounded[IO, AckableMessage[IO, ParentJob]].unsafeRunSync()
// ackableJobMessageQueue: Queue[IO, AckableMessage[IO, ParentJob]] = cats.effect.std.Queue$BoundedQueue@12a18205
val ackingJobFactory: Resource[IO, AckingQueueJobFactory[IO, AckableMessage, ParentJob]] =
  Fs2StreamJobFactory.acking(ackableJobMessageQueue)
// ackingJobFactory: Resource[IO, AckingQueueJobFactory[IO, AckableMessage, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$12665/0x00000008046fb840@63ccefc1
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$12667/0x0000000804710840@7de3e817
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$12668/0x0000000804711040@172738ad
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$12667/0x0000000804710840@1d36cc16
// )
```

### Creating a scheduler
```scala
import com.itv.scheduler.extruder.implicits._

val quartzProperties = QuartzProperties(new java.util.Properties())
// quartzProperties: QuartzProperties = QuartzProperties(properties = {})
val schedulerResource: Resource[IO, QuartzTaskScheduler[IO, ParentJob]] =
  autoAckJobFactory.flatMap { jobFactory => 
    QuartzTaskScheduler[IO, ParentJob](quartzProperties, jobFactory)
  }
// schedulerResource: Resource[IO, QuartzTaskScheduler[IO, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Bind(
//         source = Allocate(
//           resource = cats.effect.kernel.Resource$$$Lambda$12665/0x00000008046fb840@32bd730e
//         ),
//         fs = cats.effect.kernel.Resource$$Lambda$12667/0x0000000804710840@49f2493b
//       ),
//       fs = cats.effect.std.Dispatcher$$$Lambda$12668/0x0000000804711040@73b2e615
//     ),
//     fs = cats.effect.kernel.Resource$$Lambda$12667/0x0000000804710840@3972038a
//   ),
//   fs = <function1>
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
