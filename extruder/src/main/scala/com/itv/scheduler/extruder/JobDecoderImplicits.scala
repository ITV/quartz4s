package com.itv.scheduler.extruder

import cats.{MonadError, _}
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import extruder.cats.effect._
import extruder.core._
import extruder.data._
import extruder.map._
import extruder.instances._
import cats.MonadError
import com.itv.scheduler.JobDecoder
import org.quartz.JobExecutionContext
import scala.jdk.CollectionConverters._

trait JobDecoderImplicits {
  implicit def parserToJobDecoder[A: Parser]: JobDecoder[A] =
    (jobExecutionContext: JobExecutionContext) => {
      val vals: Map[String, String] =
        jobExecutionContext.getJobDetail.getJobDataMap.asScala.view.mapValues(_.toString).toMap

      //         val decoder: Decoder[F, , A, D]= implicitly
      //        val F: FlatMap[F] = implicitly
      //        val transform: Transform[F, S, I, D]

      //        val partialApplier: DecodePartiallyApplied[F, A, Settings, Map[String, String], Map[String, String]] =
      //          decodeF[F, A]
      //        partialApplier.apply(vals)(implicitly, ConcurrentEffect[F], implicitly)

      decode[A](vals).leftMap(implicitly[ValidationErrorsToThrowable].convertErrors)
    }
}
