package com.itv.scheduler

import cats.data.Chain
import com.itv.scheduler.QuartzOps.JobDataMapOps
import cats.syntax.all._
import org.quartz.JobExecutionContext
import shapeless._
import shapeless.labelled.{FieldType, field}

import scala.reflect.ClassTag
import scala.util.control.NonFatal

final case class PartiallyDecodedJobData(value: Map[Chain[String], String])

object PartiallyDecodedJobData {
  def fromMap(map: Map[String, String]) = PartiallyDecodedJobData(map.map { case (k, v) =>
    (Chain.fromSeq(k.toLowerCase.split('.').toSeq), v)
  })
}

trait JobDecoder[A] { self =>
  def apply(jobExecutionContext: JobExecutionContext): Either[Throwable, A] =
    Either.catchNonFatal(jobExecutionContext.getJobDetail.getJobDataMap.toMap).flatMap { dataMap =>
      read(Chain.empty, PartiallyDecodedJobData.fromMap(dataMap))
    }

  def read(path: Chain[String], jobData: PartiallyDecodedJobData): Either[Throwable, A]

  def emap[B](f: A => Either[Throwable, B]): JobDecoder[B] = (path: Chain[String], jobData: PartiallyDecodedJobData) =>
    self.read(path, jobData).flatMap(f)
}

object JobDecoder extends PrimitiveDecoders {
  private type Maybe[A] = Either[Throwable, A]

  def apply[A](implicit evidence: JobDecoder[A]): JobDecoder[A] = evidence

  private[scheduler] trait CoproductDecoder[Repr <: Coproduct] {
    def read(path: Chain[String], data: PartiallyDecodedJobData): Maybe[Repr]
  }

  implicit def cnilDecoder[I]: CoproductDecoder[CNil] =
    (_: Chain[String], _: PartiallyDecodedJobData) =>
      Left(new RuntimeException("Could not find specified implementation of sealed type"))

  implicit def cconsDecoder[K <: Symbol, H, Repr <: Coproduct, I](implicit
      key: Witness.Aux[K],
      headResolve: Lazy[JobDecoder[H]],
      tailResolve: Lazy[CoproductDecoder[Repr]],
      typeResolver: Lazy[JobDecoder[Option[String]]],
  ): CoproductDecoder[FieldType[K, H] :+: Repr] = (path: Chain[String], data: PartiallyDecodedJobData) => {
    def head: Maybe[FieldType[K, H] :+: Repr] = headResolve.value.read(path, data).map(v => Inl(field[K](v)))

    def tail: Maybe[FieldType[K, H] :+: Repr] = tailResolve.value.read(path, data).map(Inr(_))

    val onValidType: Option[String] => Maybe[FieldType[K, H] :+: Repr] = {
      case None                               => head.orElse(tail)
      case Some(tpe) if tpe == key.value.name => head
      case _                                  => tail
    }
    typeResolver.value.read(path :+ "type", data).flatMap(onValidType)
  }

  implicit def unionDecoder[T, V <: Coproduct](implicit
      gen: LabelledGeneric.Aux[T, V],
      underlying: Lazy[CoproductDecoder[V]],
      neOpt: T <:!< Option[_],
      neEither: T <:!< Either[_, _]
  ): JobDecoder[T] = { (path: Chain[String], jobData: PartiallyDecodedJobData) =>
    val _ = (neOpt, neEither)
    underlying.value.read(path, jobData).map(gen.from)
  }

  private[scheduler] trait HListDecoder[T, Repr <: HList] {
    def read(path: Chain[String], data: PartiallyDecodedJobData): Maybe[Repr]
  }

  implicit def hNilDerivedDecoder[T]: HListDecoder[T, HNil] =
    (_: Chain[String], _: PartiallyDecodedJobData) => HNil.asRight[Throwable]

  implicit def hConsDerivedDecoder[T, K <: Symbol, V, TailRepr <: HList, DefaultsTailRepr <: HList](implicit
      key: Witness.Aux[K],
      headDecoder: Lazy[JobDecoder[V]],
      tailDecoder: Lazy[HListDecoder[T, TailRepr]]
  ): HListDecoder[T, FieldType[K, V] :: TailRepr] =
    (path: Chain[String], data: PartiallyDecodedJobData) => {
      for {
        head <- headDecoder.value.read(path :+ key.value.name.toLowerCase, data)
        tail <- tailDecoder.value.read(path, data)
      } yield field[K](head) :: tail
    }

  implicit def productDecoder[T, GenRepr <: HList](implicit
      gen: LabelledGeneric.Aux[T, GenRepr],
      tag: ClassTag[T],
      decoder: Lazy[HListDecoder[T, GenRepr]],
      lp: LowPriority,
  ): JobDecoder[T] =
    (path: Chain[String], jobData: PartiallyDecodedJobData) => {
      val _ = lp
      decoder.value.read(path :+ tag.runtimeClass.getSimpleName.toLowerCase, jobData).map(gen.from)
    }
}

trait PrimitiveDecoders {
  private def decodeFailure[A](value: String)(implicit tag: ClassTag[A]): Throwable =
    new IllegalArgumentException(s"Unable to parse value `$value` as type ${tag.runtimeClass.getSimpleName}")

  //For 2.12 compat
  private def tryDecode[A](f: String => A)(value: String)(implicit tag: ClassTag[A]): Either[Throwable, A] =
    try Right(f(value))
    catch {
      case NonFatal(_) => Left(decodeFailure(value))
    }

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
  implicit def optionDecoder[A: JobDecoder]: JobDecoder[Option[A]] =
    (path: Chain[String], jobData: PartiallyDecodedJobData) => JobDecoder[A].read(path, jobData).toOption.asRight
  implicit def eitherDecoder[A: JobDecoder, B: JobDecoder]: JobDecoder[Either[A, B]] =
    (path: Chain[String], jobData: PartiallyDecodedJobData) =>
      JobDecoder[B].read(path, jobData).map(_.asRight[A]).orElse(JobDecoder[A].read(path, jobData).map(_.asLeft[B]))
}
