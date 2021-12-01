package com.itv.scheduler
package extruder

import cats.data.Chain
import com.itv.scheduler.{JobDataEncoder, JobDecoder}
import shapeless3.deriving.*

import scala.deriving.Mirror

trait DeriveSyntax extends DerivedDecoders with DerivedEncoders {
  inline final def deriveJobDecoder[A](using
      inline A: Mirror.Of[A],
      inline neOpt: A <:!< Option[?],
      inline neEither: A <:!< Either[?, ?]
  ): JobDecoder[A] = derivedDecoder[A]
  inline final def deriveJobDataEncoder[A](using
      inline A: Mirror.Of[A],
      inline neOpt: A <:!< Option[?],
      inline neEither: A <:!< Either[?, ?]
  ): JobDataEncoder[A] = derivedEncoder[A]

  def deriveJobCodec[A](using ev1: DerivedJobDecoder[A], ev2: DerivedJobDataEncoder[A]): JobCodec[A] =
    new JobCodec[A] {
      def read(path: Chain[String], jobData: PartiallyDecodedJobData): Either[Throwable, A] = ev1.read(path, jobData)
      def apply(a: A): Map[List[String], String]                                            = ev2.apply(a)
    }
}
