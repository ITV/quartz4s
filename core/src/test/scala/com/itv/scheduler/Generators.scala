package com.itv.scheduler

import cats.Eq
import org.quartz.JobExecutionContext
import org.scalacheck._
import org.scalacheck.derive._
import org.scalacheck.ScalacheckShapeless._

trait Generators {
  def eqJobDecoder[A](jobExecutionContext: JobExecutionContext)(implicit
      ev: Eq[Either[Throwable, A]]
  ): Eq[JobDecoder[A]] =
    Eq.by(_.decode(jobExecutionContext))

  def eqJobDataEncoder[A](data: A): Eq[JobDataEncoder[A]] = Eq.by(_.encode(data))

  implicit val eqThrowable: Eq[Throwable] = Eq.fromUniversalEquals

  implicit def eqJobCodec[A](implicit ev1: Eq[JobDecoder[A]], ev2: Eq[JobDataEncoder[A]]): Eq[JobCodec[A]] =
    Eq.and(Eq.by(_.asInstanceOf[JobDecoder[A]]), Eq.by(_.asInstanceOf[JobDataEncoder[A]]))

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
