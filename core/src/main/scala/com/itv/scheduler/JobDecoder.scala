package com.itv.scheduler

import org.quartz.JobExecutionContext

trait JobDecoder[A] {
  def apply(jobExecutionContext: JobExecutionContext): Throwable Either A
}

object JobDecoder {
//  private def bob[F[_]: ConcurrentEffect, A](
//      jobExecutionContext: JobExecutionContext
//  )(implicit mapDecoder: MapDecoder) = {
//    type Bob[B] = EffectValidation[F, B]
//
//    val vals: Map[String, String] =
//      jobExecutionContext.getJobDetail.getJobDataMap.asScala.view.mapValues(_.toString).toMap
//    decodeF[Bob, A]
//      .apply(vals)
//      .leftMap(ValidationErrorsToThrowable.apply.convertErrors(_))
//      .merge
//  }

//  def apply[F[_]: ConcurrentEffect, A]: JobDecoder[F, A] =
//    new JobDecoder[F, A] {
//      override def apply(jobExecutionContext: JobExecutionContext): F[A] = {
//        val vals: Map[String, String] =
//          jobExecutionContext.getJobDetail.getJobDataMap.asScala.view.mapValues(_.toString).toMap
//
////         val decoder: Decoder[F, , A, D]= implicitly
////        val F: FlatMap[F] = implicitly
////        val transform: Transform[F, S, I, D]
//
////        val partialApplier: DecodePartiallyApplied[F, A, Settings, Map[String, String], Map[String, String]] =
////          decodeF[F, A]
////        partialApplier.apply(vals)(implicitly, ConcurrentEffect[F], implicitly)
//        decodeF[F, A].apply(vals)
//      }
//    }

  // could not find implicit value for parameter decoder: extruder.core.Decoder[extruder.map.DecodeDefault,extruder.map.Sett,A,extruder.map.DecodeData]
  // could not find implicit value for parameter decoder: extruder.core.Decoder[cats.effect.IO,extruder.map.Sett,A,extruder.map.DecodeData]
}
