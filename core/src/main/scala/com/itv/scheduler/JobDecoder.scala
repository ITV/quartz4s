package com.itv.scheduler

import cats.Functor
import cats.data.Chain
import cats.syntax.all.*
import com.itv.scheduler.QuartzOps.JobDataMapOps
import org.quartz.JobExecutionContext

trait JobDecoder[A] { self =>
  def decode(jobExecutionContext: JobExecutionContext): Either[Throwable, A] =
    Either.catchNonFatal(jobExecutionContext.getJobDetail.getJobDataMap.toMap).flatMap { dataMap =>
      read(Chain.empty, PartiallyDecodedJobData.fromMap(dataMap))
    }

  private[scheduler] def read(path: Chain[String], jobData: PartiallyDecodedJobData): Either[Throwable, A]

  def emap[B](f: A => Either[Throwable, B]): JobDecoder[B] = (path: Chain[String], jobData: PartiallyDecodedJobData) =>
    self.read(path, jobData).flatMap(f)
}

object JobDecoder {
  implicit val functorInstance: Functor[JobDecoder] =
    new Functor[JobDecoder] {
      override def map[A, B](fa: JobDecoder[A])(f: A => B): JobDecoder[B] =
        JobDecoder.instance[B](fa.read(_, _).map(f))
    }

  def apply[A](implicit ev: JobDecoder[A]): JobDecoder[A] = ev

  def instance[A](read: (Chain[String], PartiallyDecodedJobData) => Either[Throwable, A]): JobDecoder[A] =
    (path: Chain[String], jobData: PartiallyDecodedJobData) => read(path, jobData)
}

final case class PartiallyDecodedJobData(value: Map[Chain[String], String])

object PartiallyDecodedJobData {
  def fromMap(map: Map[String, String]): PartiallyDecodedJobData = PartiallyDecodedJobData(map.map { case (k, v) =>
    (Chain.fromSeq(k.toLowerCase.split('.').toSeq), v)
  })
}
