package com.itv.scheduler
package extruder

import cats.data.Chain
import shapeless._

trait DeriveSyntax {
  def deriveJobDecoder[A](implicit ev: Lazy[DerivedJobDecoder[A]]): JobDecoder[A] = ev.value
  def deriveJobEncoder[A](implicit ev: Lazy[DerivedJobDataEncoder[A]]): JobDataEncoder[A] = ev.value
  def deriveJobCodec[A](implicit ev1: Lazy[DerivedJobDecoder[A]], ev2: Lazy[DerivedJobDataEncoder[A]]): JobCodec[A] =
    new JobCodec[A] {
      def read(path: Chain[String], jobData: PartiallyDecodedJobData): Either[Throwable, A] =
        ev1.value.read(path, jobData)
      def apply(a: A): Map[List[String], String] = ev2.value.apply(a)
    }
}
