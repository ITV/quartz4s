package com.itv.scheduler.extruder

import java.util.UUID

import cats.data.Chain
import cats.syntax.all._
import com.itv.scheduler.{JobDecoder, PartiallyDecodedJobData}

import scala.reflect.ClassTag

trait PrimitiveDecoders {
  private def decodeFailure[A](value: String)(implicit tag: ClassTag[A]): Throwable =
    new IllegalArgumentException(s"Unable to parse value `$value` as type ${tag.runtimeClass.getSimpleName}")

  //For 2.12 compat
  private def tryDecode[A: ClassTag](f: String => A)(value: String): Either[Throwable, A] =
    Either.catchNonFatal(f(value)).leftMap(_ => decodeFailure(value))

  implicit val stringDecoder: JobDecoder[String] = (path: Chain[String], jobData: PartiallyDecodedJobData) =>
    Either.fromOption(
      jobData.value.get(path),
      new NoSuchElementException(s"Value missing at path `${path.mkString_(".")}``")
    )
  implicit val booleanDecoder: JobDecoder[Boolean] =
    stringDecoder.emap(tryDecode(_.toBoolean))
  implicit val intDecoder: JobDecoder[Int] =
    stringDecoder.emap(tryDecode(_.toInt))
  implicit val longDecoder: JobDecoder[Long] =
    stringDecoder.emap(tryDecode(_.toLong))
  implicit val charDecoder: JobDecoder[Char] =
    stringDecoder.emap(s => s.headOption.toRight(decodeFailure(s)))
  implicit val doubleDecoder: JobDecoder[Double] =
    stringDecoder.emap(tryDecode(_.toDouble))
  implicit val floatDecoder: JobDecoder[Float] =
    stringDecoder.emap(tryDecode(_.toFloat))
  implicit val uuidDecoder: JobDecoder[UUID] =
    stringDecoder.emap(tryDecode(UUID.fromString))
  implicit def optionDecoder[A: JobDecoder]: JobDecoder[Option[A]] =
    (path: Chain[String], jobData: PartiallyDecodedJobData) => JobDecoder[A].read(path, jobData).toOption.asRight
  implicit def eitherDecoder[A: JobDecoder, B: JobDecoder]: JobDecoder[Either[A, B]] =
    (path: Chain[String], jobData: PartiallyDecodedJobData) =>
      JobDecoder[B].read(path, jobData).map(_.asRight[A]).orElse(JobDecoder[A].read(path, jobData).map(_.asLeft[B]))
}

object PrimitiveDecoders extends PrimitiveDecoders
