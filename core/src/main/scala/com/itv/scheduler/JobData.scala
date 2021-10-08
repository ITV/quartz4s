package com.itv.scheduler

import cats.Eq

final case class JobData(dataMap: Map[String, String])
object JobData {
  implicit val eqInstance: Eq[JobData] = Eq.fromUniversalEquals
}
