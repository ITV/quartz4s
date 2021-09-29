package com.itv.scheduler.extruder

import java.util.UUID

import com.itv.scheduler.JobDataEncoder

trait PrimitiveEncoders {
  private def makeFromToString[A]: JobDataEncoder[A]   = (a: A) => Map(Nil -> a.toString)
  implicit val stringEncoder: JobDataEncoder[String]   = makeFromToString
  implicit val booleanEncoder: JobDataEncoder[Boolean] = makeFromToString
  implicit val intEncoder: JobDataEncoder[Int]         = makeFromToString
  implicit val longEncoder: JobDataEncoder[Long]       = makeFromToString
  implicit val doubleEncoder: JobDataEncoder[Double]   = makeFromToString
  implicit val floatEncoder: JobDataEncoder[Float]     = makeFromToString
  implicit val charEncoder: JobDataEncoder[Char]       = makeFromToString
  implicit val uuidEncoder: JobDataEncoder[UUID]       = makeFromToString
  implicit def optionEncoder[A: JobDataEncoder]: JobDataEncoder[Option[A]] = (_: Option[A]) match {
    case Some(a) => JobDataEncoder[A].apply(a)
    case None    => Map()
  }
  implicit def eitherEncoder[L: JobDataEncoder, R: JobDataEncoder]: JobDataEncoder[Either[L, R]] =
    (_: Either[L, R]) match {
      case Left(value)  => JobDataEncoder[L].apply(value)
      case Right(value) => JobDataEncoder[R].apply(value)
    }
}

object PrimitiveEncoders extends PrimitiveEncoders
