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

  inline final def deriveJobCodec[A](using
      inline A: Mirror.Of[A],
      inline neOpt: A <:!< Option[?],
      inline neEither: A <:!< Either[?, ?]
  ): JobCodec[A] = JobCodec.from[A](deriveJobDecoder[A], derivedEncoder[A])
}
