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
import fs2.concurrent.Queue
import scala.concurrent.ExecutionContext

implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
// contextShift: ContextShift[IO] = cats.effect.internals.IOContextShift@37fe16dd

val jobMessageQueue = Queue.unbounded[IO, ParentJob].unsafeRunSync()
// jobMessageQueue: Queue[IO, ParentJob] = fs2.concurrent.Queue$InPartiallyApplied$$anon$3@61dd8495
val autoAckJobFactory = Fs2StreamJobFactory.autoAcking[IO, ParentJob](jobMessageQueue)
// autoAckJobFactory: AutoAckingQueueJobFactory[IO, ParentJob] = com.itv.scheduler.AutoAckingQueueJobFactory@5fb0fe01
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
// ackableJobResourceMessageQueue: Queue[IO, Resource[IO, ParentJob]] = fs2.concurrent.Queue$InPartiallyApplied$$anon$3@4cb3f681
val ackingResourceJobFactory: AckingQueueJobFactory[IO, Resource, ParentJob] =
  Fs2StreamJobFactory.ackingResource(ackableJobResourceMessageQueue)
// ackingResourceJobFactory: AckingQueueJobFactory[IO, Resource, ParentJob] = com.itv.scheduler.AckingQueueJobFactory@3a1ea4a0

// each message is wrapped as a `AckableMessage` which acks on completion
val ackableJobMessageQueue = Queue.unbounded[IO, AckableMessage[IO, ParentJob]].unsafeRunSync()
// ackableJobMessageQueue: Queue[IO, AckableMessage[IO, ParentJob]] = fs2.concurrent.Queue$InPartiallyApplied$$anon$3@22faf862
val ackingJobFactory: AckingQueueJobFactory[IO, AckableMessage, ParentJob] =
  Fs2StreamJobFactory.acking(ackableJobMessageQueue)
// ackingJobFactory: AckingQueueJobFactory[IO, AckableMessage, ParentJob] = com.itv.scheduler.AckingQueueJobFactory@73a20a10
```

### Creating a scheduler
```scala
import java.util.concurrent.Executors
import com.itv.scheduler.extruder.implicits._

val quartzProperties = QuartzProperties(new java.util.Properties())
// quartzProperties: QuartzProperties = QuartzProperties(properties = {})
val blocker = Blocker.liftExecutorService(Executors.newFixedThreadPool(8))
// blocker: Blocker = cats.effect.Blocker@55f064ca
val schedulerResource: Resource[IO, QuartzTaskScheduler[IO, ParentJob]] =
  QuartzTaskScheduler[IO, ParentJob](blocker, quartzProperties, autoAckJobFactory)
// schedulerResource: Resource[IO, QuartzTaskScheduler[IO, ParentJob]] = Allocate(
//   resource = Map(
//     source = Map(
//       source = Bind(
//         source = Delay(
//           thunk = com.itv.scheduler.QuartzTaskScheduler$$$Lambda$17567/0x0000000804277040@300e91f1
//         ),
//         f = cats.FlatMap$$Lambda$17569/0x0000000804275840@6e4c88b1,
//         trace = StackTrace(
//           stackTrace = List(
//             cats.effect.internals.IOTracing$.buildFrame(IOTracing.scala:48),
//             cats.effect.internals.IOTracing$.buildCachedFrame(IOTracing.scala:39),
//             cats.effect.internals.IOTracing$.cached(IOTracing.scala:34),
//             cats.effect.IO.flatMap(IO.scala:133),
//             cats.effect.IOLowPriorityInstances$IOEffect.flatMap(IO.scala:886),
//             cats.effect.IOLowPriorityInstances$IOEffect.flatMap(IO.scala:863),
//             cats.FlatMap.flatTap(FlatMap.scala:154),
//             cats.FlatMap.flatTap$(FlatMap.scala:153),
//             cats.effect.IOLowPriorityInstances$IOEffect.flatTap(IO.scala:863),
//             cats.FlatMap$Ops.flatTap(FlatMap.scala:234),
//             cats.FlatMap$Ops.flatTap$(FlatMap.scala:234),
//             cats.FlatMap$ToFlatMapOps$$anon$2.flatTap(FlatMap.scala:243),
//             com.itv.scheduler.QuartzTaskScheduler$.apply(QuartzTaskScheduler.scala:105),
//             repl.MdocSession$App.<init>(README.md:89),
//             repl.MdocSession$.app(README.md:3),
//             mdoc.internal.document.DocumentBuilder$$doc$.$anonfun$build$2(DocumentBuilder.scala:89),
//             scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18),
//             scala.util.DynamicVariable.withValue(DynamicVariable.scala:59),
//             scala.Console$.withErr(Console.scala:193),
//             mdoc.internal.document.DocumentBuilder$$doc$.$anonfun$build$1(DocumentBuilder.scala:89),
//             scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18),
//             scala.util.DynamicVariable.withValue(DynamicVariable.scala:59),
//             scala.Console$.withOut(Console.scala:164),
//             mdoc.internal.document.DocumentBuilder$$doc$.build(DocumentBuilder.scala:88),
//             mdoc.internal.markdown.MarkdownBuilder$.buildDocument(MarkdownBuilder.scala:44),
//             mdoc.internal.markdown.Processor.processScalaInputs(Processor.scala:185),
//             mdoc.internal.markdown.Processor.processScalaInputs(Processor.scala:152),
//             mdoc.internal.markdown.Processor.processDocument(Processor.scala:52)...
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
