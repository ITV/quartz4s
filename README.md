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
// jobMessageQueue: Queue[IO, ParentJob] = cats.effect.std.Queue$BoundedQueue@5ad1256f
val autoAckJobFactory = Fs2StreamJobFactory.autoAcking[IO, ParentJob](jobMessageQueue)
// autoAckJobFactory: Resource[IO, AutoAckingQueueJobFactory[IO, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$6008/0x00000008026bb840@7ba49f70
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$6010/0x00000008026ba040@4036c2e7
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$6011/0x00000008026b9040@3fac7b29
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$6010/0x00000008026ba040@591cd230
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
// ackableJobResourceMessageQueue: Queue[IO, Resource[IO, ParentJob]] = cats.effect.std.Queue$BoundedQueue@4d452f7c
val ackingResourceJobFactory: Resource[IO, AckingQueueJobFactory[IO, Resource, ParentJob]] =
  Fs2StreamJobFactory.ackingResource(ackableJobResourceMessageQueue)
// ackingResourceJobFactory: Resource[IO, AckingQueueJobFactory[IO, Resource, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$6008/0x00000008026bb840@3ad78117
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$6010/0x00000008026ba040@5e8d77fe
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$6011/0x00000008026b9040@137a0fd0
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$6010/0x00000008026ba040@43560437
// )

// each message is wrapped as a `AckableMessage` which acks on completion
val ackableJobMessageQueue = Queue.unbounded[IO, AckableMessage[IO, ParentJob]].unsafeRunSync()
// ackableJobMessageQueue: Queue[IO, AckableMessage[IO, ParentJob]] = cats.effect.std.Queue$BoundedQueue@334fc2f5
val ackingJobFactory: Resource[IO, AckingQueueJobFactory[IO, AckableMessage, ParentJob]] =
  Fs2StreamJobFactory.acking(ackableJobMessageQueue)
// ackingJobFactory: Resource[IO, AckingQueueJobFactory[IO, AckableMessage, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$6008/0x00000008026bb840@48aa9cee
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$6010/0x00000008026ba040@5bfa764
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$6011/0x00000008026b9040@1d942373
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$6010/0x00000008026ba040@ae6840f
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
//           resource = cats.effect.kernel.Resource$$$Lambda$6008/0x00000008026bb840@7ba49f70
//         ),
//         fs = cats.effect.kernel.Resource$$Lambda$6010/0x00000008026ba040@4036c2e7
//       ),
//       fs = cats.effect.std.Dispatcher$$$Lambda$6011/0x00000008026b9040@3fac7b29
//     ),
//     fs = cats.effect.kernel.Resource$$Lambda$6010/0x00000008026ba040@591cd230
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
