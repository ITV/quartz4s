package com.itv.scheduler

import cats.Eq
import org.scalacheck._
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck.derive._

trait Generators {
  // Not quite sure how to define these any other way ...
  implicit def eqJobDecoder[A]: Eq[JobDecoder[A]]         = Eq.allEqual[JobDecoder[A]]
  implicit def eqJobDataEncoder[A]: Eq[JobDataEncoder[A]] = Eq.allEqual[JobDataEncoder[A]]
  implicit def eqJobCodec[A]: Eq[JobCodec[A]]             = Eq.allEqual[JobCodec[A]]

  implicit val jobWithNestingGen: Arbitrary[JobWithNesting] =
    MkArbitrary[JobWithNesting].arbitrary

  implicit def jobDecoderFromJobCodecGen[A](implicit decoder: JobDecoder[A]): Arbitrary[JobDecoder[A]] =
    Arbitrary(Gen.const(decoder))

  implicit def jobDataEncoderFromJobCodecGen[A](implicit encoder: JobDataEncoder[A]): Arbitrary[JobDataEncoder[A]] =
    Arbitrary(Gen.const(encoder))

  implicit def jobCodecFromJobCodecGen[A](implicit codec: JobCodec[A]): Arbitrary[JobCodec[A]] =
    Arbitrary(Gen.const(codec))

  implicit val jobWithNestingCogen: Cogen[JobWithNesting] =
    MkCogen[JobWithNesting].cogen
}
