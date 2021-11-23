package com.itv.scheduler

import cats.Eq
import com.itv.scheduler.extruder.*
import com.itv.scheduler.extruder.semiauto.*

sealed trait ParentTestJob
case class UserJob(id: UserId) extends ParentTestJob
object UserJob {
  implicit val eqInstance: Eq[UserJob] = Eq.by(_.id)
}
case object ChildObjectJob extends ParentTestJob

object ParentTestJob {
  implicit val jobCodec: JobCodec[ParentTestJob] = deriveJobCodec
}

final case class JobWithNesting(a: String, b: Option[Boolean], c: ParentTestJob, d: Option[Int])

object JobWithNesting {
  implicit val jobCodec: JobCodec[JobWithNesting] = deriveJobCodec

  implicit val eqInstance: Eq[JobWithNesting] = Eq.fromUniversalEquals
}
