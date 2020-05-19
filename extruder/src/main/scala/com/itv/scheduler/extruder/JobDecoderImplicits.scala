package com.itv.scheduler.extruder

import cats.{MonadError, _}
import cats.effect._
import cats.effect.implicits._
import cats.instances.all._
import extruder.cats.effect._
import extruder.core._
import extruder.data._
import extruder.map._
import cats.MonadError
import com.itv.scheduler.JobDecoder
import org.quartz.JobExecutionContext
import scala.jdk.CollectionConverters._

trait JobDecoderImplicits {
  implicit def apply[F[_], A](implicit F: MonadError[F, Throwable], decoder: MultiParser[F, A]): JobDecoder[F, A] =
    new JobDecoder[F, A] {
      override def apply(jobExecutionContext: JobExecutionContext): F[A] = {
        val vals: Map[String, String] =
          jobExecutionContext.getJobDetail.getJobDataMap.asScala.view.mapValues(_.toString).toMap

        //         val decoder: Decoder[F, , A, D]= implicitly
        //        val F: FlatMap[F] = implicitly
        //        val transform: Transform[F, S, I, D]

        //        val partialApplier: DecodePartiallyApplied[F, A, Settings, Map[String, String], Map[String, String]] =
        //          decodeF[F, A]
        //        partialApplier.apply(vals)(implicitly, ConcurrentEffect[F], implicitly)
        decodeF[F, A](vals)
      }
    }
}
