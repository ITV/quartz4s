package com.itv.scheduler.extruder

import com.itv.scheduler.{JobDataEncoder, JobDecoder}
import extruder.core._
import extruder.map._

package object implicits extends JobDataEncoderImplicits with JobDecoderImplicits {
  implicit class JobDataEncoderW(_type: JobDataEncoder.type) {
    def derive[A](implicit enc: Encoder[EncodeDefault, Sett, A, EncodeData]): JobDataEncoder[A] =
      deriveEncoder[A]
  }

  implicit class JobDecoderW(_type: JobDecoder.type) {
    def derive[A](implicit dec: Decoder[DecodeDefault, Sett, A, DecodeData]): JobDecoder[A] =
      deriveDecoder[A]
  }
}
