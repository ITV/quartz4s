package com.itv.scheduler.extruder

import com.itv.scheduler.{JobData, JobDataEncoder}
import extruder.core._
import extruder.map._

trait JobDataEncoderImplicits {
  implicit def showToJobDataEncoder[A](implicit show: Show[A]): JobDataEncoder[A] =
    (a: A) => JobData(encode(a))
}
