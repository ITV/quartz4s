package com.itv.scheduler

import cats.syntax.all._
import extruder.primitives._

case class UserId(value: String)
object UserId {
  implicit val jobCodec: JobCodec[UserId] = JobCodec.from[String].imap(UserId(_))(_.value)
}
