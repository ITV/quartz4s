package com.itv.scheduler.extruder

import cats.data.Chain
import cats.implicits._
import com.itv.scheduler.{JobDecoder, PartiallyDecodedJobData}
import shapeless._
import shapeless.labelled.{FieldType, field}

import scala.reflect.ClassTag

trait DerivedJobDecoder[A] extends JobDecoder[A]

trait DerivedDecoders extends PrimitiveDecoders {
  private type Maybe[A] = Either[Throwable, A]

  sealed abstract class CoproductDecoder[Repr <: Coproduct] {
    def read(path: Chain[String], data: PartiallyDecodedJobData): Maybe[Repr]
  }

  implicit def cNilDecoder: CoproductDecoder[CNil] = new CoproductDecoder[CNil] {
    def read(path: Chain[String], data: PartiallyDecodedJobData): Maybe[CNil] = Left(
      new RuntimeException("Could not find specified implementation of sealed type")
    )
  }

  implicit def cConsDecoder[K <: Symbol, H, Repr <: Coproduct](implicit
      key: Witness.Aux[K],
      headResolve: Lazy[JobDecoder[H]],
      tailResolve: Lazy[CoproductDecoder[Repr]],
  ): CoproductDecoder[FieldType[K, H] :+: Repr] =
    new CoproductDecoder[FieldType[K, H] :+: Repr] {
      def read(path: Chain[String], data: PartiallyDecodedJobData): Maybe[FieldType[K, H] :+: Repr] = {
        def head: Maybe[FieldType[K, H] :+: Repr] = headResolve.value.read(path, data).map(v => Inl(field[K](v)))

        def tail: Maybe[FieldType[K, H] :+: Repr] = tailResolve.value.read(path, data).map(Inr(_))

        val onValidType: Option[String] => Maybe[FieldType[K, H] :+: Repr] = {
          case None                               => head.orElse(tail)
          case Some(tpe) if tpe == key.value.name => head
          case _                                  => tail
        }
        optionDecoder(stringDecoder).read(path :+ "type", data).flatMap(onValidType)
      }
    }

  implicit def unionDecoder[T, V <: Coproduct](implicit
      gen: LabelledGeneric.Aux[T, V],
      underlying: Lazy[CoproductDecoder[V]],
      neOpt: T <:!< Option[_],
      neEither: T <:!< Either[_, _]
  ): DerivedJobDecoder[T] = { (path: Chain[String], jobData: PartiallyDecodedJobData) =>
    val _ = (neOpt, neEither)
    underlying.value.read(path, jobData).map(gen.from)
  }

  sealed abstract class HListDecoder[Repr <: HList] {
    def read(path: Chain[String], data: PartiallyDecodedJobData): Maybe[Repr]
  }

  implicit def hNilDecoder: HListDecoder[HNil] = new HListDecoder[HNil] {
    def read(path: Chain[String], data: PartiallyDecodedJobData): Maybe[HNil] = HNil.asRight[Throwable]
  }

  implicit def hConsDecoder[K <: Symbol, V, TailRepr <: HList, DefaultsTailRepr <: HList](implicit
      key: Witness.Aux[K],
      headDecoder: Lazy[JobDecoder[V]],
      tailDecoder: Lazy[HListDecoder[TailRepr]]
  ): HListDecoder[FieldType[K, V] :: TailRepr] =
    new HListDecoder[FieldType[K, V] :: TailRepr] {
      def read(path: Chain[String], data: PartiallyDecodedJobData): Maybe[FieldType[K, V] :: TailRepr] =
        for {
          head <- headDecoder.value.read(path :+ key.value.name.toLowerCase, data)
          tail <- tailDecoder.value.read(path, data)
        } yield field[K](head) :: tail
    }

  implicit def productDecoder[T, GenRepr <: HList](implicit
      gen: LabelledGeneric.Aux[T, GenRepr],
      tag: ClassTag[T],
      decoder: Lazy[HListDecoder[GenRepr]],
      lp: LowPriority,
  ): DerivedJobDecoder[T] =
    (path: Chain[String], jobData: PartiallyDecodedJobData) => {
      val _ = lp
      decoder.value.read(path :+ tag.runtimeClass.getSimpleName.toLowerCase, jobData).map(gen.from)
    }
}
