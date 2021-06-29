package com.itv.scheduler

import extruder.semiauto._

sealed trait ParentTestJob
case class UserJob(id: String) extends ParentTestJob
case object ChildObjectJob     extends ParentTestJob

object ParentTestJob {
  implicit val jobCodec: JobCodec[ParentTestJob] = deriveJobCodec
}

final case class JobWithNesting(a: String, b: Option[Boolean], c: ParentTestJob, d: Option[Int])

object JobWithNesting {
  implicit val jobCodec: JobCodec[JobWithNesting] = deriveJobCodec
}
