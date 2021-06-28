package com.itv.scheduler

sealed trait ParentTestJob
case class UserJob(id: String) extends ParentTestJob
case object ChildObjectJob     extends ParentTestJob

final case class JobWithNesting(a: String, b: Option[Boolean], c: ParentTestJob, d: Option[Int])
