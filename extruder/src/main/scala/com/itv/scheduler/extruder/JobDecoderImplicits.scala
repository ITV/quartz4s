package com.itv.scheduler.extruder

import cats.implicits._
import com.itv.scheduler.{JobDecoder, QuartzOps}
import extruder.core._
import extruder.map._
import org.quartz.JobExecutionContext

trait JobDecoderImplicits extends QuartzOps {
  implicit def deriveDecoder[A](implicit dec: Decoder[DecodeDefault, Sett, A, DecodeData]): JobDecoder[A] =
    (jobExecutionContext: JobExecutionContext) => {
      Either
        .catchNonFatal(jobExecutionContext.jobDataMap)
        .flatMap(decode[A](_).leftMap(implicitly[ValidationErrorsToThrowable].convertErrors))
    }
}
