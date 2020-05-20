package com.itv.scheduler.extruder

import cats.implicits._
import com.itv.scheduler.JobDecoder
import extruder.core._
import extruder.map._
import org.quartz.JobExecutionContext

import scala.collection.JavaConverters._

trait JobDecoderImplicits {
  implicit def deriveDecoder[A](implicit dec: Decoder[DecodeDefault, Sett, A, DecodeData]): JobDecoder[A] =
    (jobExecutionContext: JobExecutionContext) => {
      Either
        .catchNonFatal(
          jobExecutionContext.getJobDetail.getJobDataMap.asScala.mapValues(_.toString).toMap
        )
        .flatMap(decode[A](_).leftMap(implicitly[ValidationErrorsToThrowable].convertErrors))
    }
}
