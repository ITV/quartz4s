package com.itv.scheduler
package extruder

import cats.data.Chain
import com.itv.scheduler.{JobDataEncoder, JobDecoder}
import shapeless3.deriving.*

trait DeriveSyntax {
//  given deriveJobDecoder[A](implicit ev: => DerivedJobDecoder[A]): JobDecoder[A]             = ev
//  def deriveJobDataEncoder[A](implicit ev: => DerivedJobDataEncoder[A]): JobDataEncoder[A] = ev
  implicit def deriveJobCodec[A](implicit ev1: => DerivedJobDecoder[A], ev2: => DerivedJobDataEncoder[A]): JobCodec[A] =
    new JobCodec[A] {
      def read(path: Chain[String], jobData: PartiallyDecodedJobData): Either[Throwable, A] = ev1.read(path, jobData)
      def apply(a: A): Map[List[String], String]                                            = ev2.apply(a)
    }

  /*
  def deriveJobDecoder[A](implicit ev: => DerivedJobDecoder[A]): JobDecoder[A]             = ev
  def deriveJobDataEncoder[A](implicit ev: => DerivedJobDataEncoder[A]): JobDataEncoder[A] = ev
  def deriveJobCodec[A](implicit ev1: => DerivedJobDecoder[A], ev2: => DerivedJobDataEncoder[A]): JobCodec[A] =
    new JobCodec[A] {
      def read(path: Chain[String], jobData: PartiallyDecodedJobData): Either[Throwable, A] = ev1.read(path, jobData)
      def apply(a: A): Map[List[String], String]                                            = ev2.apply(a)
    }
  */
}
