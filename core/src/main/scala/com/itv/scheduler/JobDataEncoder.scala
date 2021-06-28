package com.itv.scheduler

import cats.Contravariant
import shapeless._
import shapeless.labelled.FieldType

import scala.reflect.ClassTag

trait JobDataEncoder[A] {
  def apply(a: A): Map[List[String], String]
  def toJobData(a: A): JobData =
    JobData(apply(a).map { case (keys, value) =>
      (keys.mkString(".").toLowerCase, value)
    })
}

object JobDataEncoder extends PrimitiveEncoders {
  implicit val contravariantJobDataEncoder: Contravariant[JobDataEncoder] = new Contravariant[JobDataEncoder] {
    override def contramap[A, B](fa: JobDataEncoder[A])(f: B => A): JobDataEncoder[B] =
      (b: B) => fa(f(b))
  }

  def apply[A](implicit evidence: JobDataEncoder[A]): JobDataEncoder[A] = evidence

  private[scheduler] trait DerivedEncoder[T, Repr <: HList] {
    def encode(r: Repr): Map[List[String], String]
  }

  implicit val cnilEncoder: JobDataEncoder[CNil] = (_: CNil) => sys.error("Impossible!")

  implicit def cconsEncoder[F[_], K <: Symbol, H, T <: Coproduct, S, D](implicit
      key: Witness.Aux[K],
      headEncode: JobDataEncoder[H],
      tailEncode: Lazy[JobDataEncoder[T]],
  ): JobDataEncoder[FieldType[K, H] :+: T] = {
    case Inl(head) => headEncode.apply(head) + (List("type") -> key.value.name)
    case Inr(tail) => tailEncode.value.apply(tail)
  }

  implicit def unionEncoder[T, Repr <: Coproduct](implicit
      gen: LabelledGeneric.Aux[T, Repr],
      underlying: Lazy[JobDataEncoder[Repr]],
      lp: LowPriority,
      neOpt: T <:!< Option[_],
      neEither: T <:!< Either[_, _]
  ): JobDataEncoder[T] = (a: T) => {
    val _ = (lp, neOpt, neEither)
    underlying.value.apply(gen.to(a))
  }

  implicit def hNilDerivedDecoder[T]: DerivedEncoder[T, HNil] = (_: HNil) => Map.empty

  implicit def hConsDerivedEncoder[T, K <: Symbol, V, TailRepr <: HList](implicit
      key: Witness.Aux[K],
      encoder: Lazy[JobDataEncoder[V]],
      tailEncoder: Lazy[DerivedEncoder[T, TailRepr]]
  ): DerivedEncoder[T, FieldType[K, V] :: TailRepr] =
    (r: FieldType[K, V] :: TailRepr) => {
      val fieldName = key.value.name
      encoder.value.apply(r.head).map { case (key, value) => (fieldName :: key, value) } ++
        tailEncoder.value.encode(r.tail)
    }

  implicit def deriveEncoder[In, Repr <: HList](implicit
      gen: LabelledGeneric.Aux[In, Repr],
      tag: ClassTag[In],
      lp: LowPriority,
      encoder: Lazy[DerivedEncoder[In, Repr]]
  ): JobDataEncoder[In] = (a: In) => {
    val _ = lp
    encoder.value.encode(gen.to(a)).map { case (key, value) =>
      (tag.runtimeClass.getSimpleName :: key, value)
    }
  }

}

trait PrimitiveEncoders {
  private def makeFromToString[A]: JobDataEncoder[A]   = (a: A) => Map(Nil -> a.toString)
  implicit val stringEncoder: JobDataEncoder[String]   = makeFromToString
  implicit val booleanEncoder: JobDataEncoder[Boolean] = makeFromToString
  implicit val intEncoder: JobDataEncoder[Int]         = makeFromToString
  implicit val longEncoder: JobDataEncoder[Long]       = makeFromToString
  implicit val doubleEncoder: JobDataEncoder[Double]   = makeFromToString
  implicit val floatEncoder: JobDataEncoder[Float]     = makeFromToString
  implicit val charEncoder: JobDataEncoder[Char]       = makeFromToString
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
