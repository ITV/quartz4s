package com.itv.scheduler.extruder

import com.itv.scheduler.{JobData, JobDataEncoder}
import extruder.core._
import extruder.map._

trait JobDataEncoderImplicits {
  implicit def deriveEncoder[A](implicit enc: Encoder[EncodeDefault, Sett, A, EncodeData]): JobDataEncoder[A] =
    (a: A) => JobData(encode(a))
}
