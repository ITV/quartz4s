package com.itv.scheduler

import com.itv.scheduler.extruder.implicits._
import _root_.extruder.map._

sealed trait ParentTestJob
case object ChildObjectJob     extends ParentTestJob
case class UserJob(id: String) extends ParentTestJob

object ParentTestJob {
  implicit val jobDataEncoder: JobDataEncoder[ParentTestJob] = deriveEncoder[ParentTestJob]
  implicit val jobDecoder: JobDecoder[ParentTestJob]         = deriveDecoder[ParentTestJob]
}
