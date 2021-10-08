package com.itv.scheduler

import cats.Eq
import cats.syntax.all._
import extruder.primitives._

case class UserId(value: String)
object UserId {
  implicit val jobCodec: JobCodec[UserId] = JobCodec.from[String].imap(UserId(_))(_.value)

  implicit val eqInstance: Eq[UserId] = Eq.by(_.value)
}
