package com.itv.scheduler

import cats.Eq
import com.itv.scheduler.extruder.semiauto.*

sealed trait ParentTestJob
case class UserJob(id: UserId) extends ParentTestJob
object UserJob {
  implicit val eqInstance: Eq[UserJob] = Eq.by(_.id)
}
case object ChildObjectJob extends ParentTestJob

object ParentTestJob {
  implicit val jobDecoder: JobDecoder[ParentTestJob]     = deriveJobDecoder
  implicit val jobEncoder: JobDataEncoder[ParentTestJob] = deriveJobDataEncoder
}

final case class JobWithNesting(a: String, b: Option[Boolean], c: ParentTestJob, d: Option[Int])

object JobWithNesting {
  implicit val jobDecoder: JobDecoder[JobWithNesting]     = deriveJobDecoder
  implicit val jobEncoder: JobDataEncoder[JobWithNesting] = deriveJobDataEncoder

  implicit val eqInstance: Eq[JobWithNesting] = Eq.fromUniversalEquals
}
