package com.itv.scheduler.extruder

import com.itv.scheduler.{JobData, JobDataEncoder}
//import shapeless3.*
//import shapeless3.labelled.FieldType

import scala.reflect.ClassTag
import scala.deriving.Mirror

trait DerivedJobDataEncoder[A] extends JobDataEncoder[A]

trait DerivedEncoders extends PrimitiveEncoders {

  given [P <: Product](using m: Mirror.ProductOf[P], ts: JobDataEncoder[m.MirroredElemTypes]): DerivedJobDataEncoder[P] =
    new DerivedJobDataEncoder[P] {
      override private[scheduler] def apply(p: P) =
        ts.apply(Tuple.fromProductTyped(p))

//      override def encode(a: P): JobData =
//        ts.encode(Tuple.fromProductTyped(a))
    }

//
//  sealed abstract class CoproductEncoder[Repr <: Coproduct] {
//    def apply(a: Repr): Map[List[String], String]
//  }
//
//  implicit def cNilEncoder: CoproductEncoder[CNil] = new CoproductEncoder[CNil] {
//    def apply(a: CNil): Map[List[String], String] = sys.error("Impossible!")
//  }
//
//  implicit def cConsEncoder[F[_], K <: Symbol, H, T <: Coproduct, S, D](implicit
//      key: Witness.Aux[K],
//      headEncode: JobDataEncoder[H],
//      tailEncode: Lazy[CoproductEncoder[T]],
//  ): CoproductEncoder[FieldType[K, H] :+: T] = new CoproductEncoder[FieldType[K, H] :+: T] {
//    def apply(a: FieldType[K, H] :+: T): Map[List[String], String] = a match {
//      case Inl(head) => headEncode.apply(head) + (List("type") -> key.value.name)
//      case Inr(tail) => tailEncode.value.apply(tail)
//    }
//  }
//
//  implicit def unionEncoder[T, Repr <: Coproduct](implicit
//      gen: LabelledGeneric.Aux[T, Repr],
//      underlying: Lazy[CoproductEncoder[Repr]],
//      lp: LowPriority,
//      neOpt: T <:!< Option[_],
//      neEither: T <:!< Either[_, _]
//  ): DerivedJobDataEncoder[T] = (a: T) => {
//    val _ = (lp, neOpt, neEither)
//    underlying.value.apply(gen.to(a))
//  }
//
//  sealed abstract class HListEncoder[Repr <: HList] {
//    def apply(a: Repr): Map[List[String], String]
//  }
//
//  implicit def hNilEncoder: HListEncoder[HNil] = new HListEncoder[HNil] {
//    def apply(a: HNil): Map[List[String], String] = Map.empty
//  }
//
//  implicit def hConsEncoder[K <: Symbol, V, TailRepr <: HList](implicit
//      key: Witness.Aux[K],
//      encoder: JobDataEncoder[V],
//      tailEncoder: Lazy[HListEncoder[TailRepr]]
//  ): HListEncoder[FieldType[K, V] :: TailRepr] = new HListEncoder[FieldType[K, V] :: TailRepr] {
//    def apply(a: FieldType[K, V] :: TailRepr): Map[List[String], String] = {
//      val fieldName = key.value.name
//      encoder.apply(a.head).map { case (key, value) => (fieldName :: key, value) } ++
//        tailEncoder.value.apply(a.tail)
//    }
//  }
//
//  implicit def productEncoder[In, Repr <: HList](implicit
//      gen: LabelledGeneric.Aux[In, Repr],
//      tag: ClassTag[In],
//      lp: LowPriority,
//      encoder: Lazy[HListEncoder[Repr]]
//  ): DerivedJobDataEncoder[In] = (a: In) => {
//    val _ = lp
//    encoder.value.apply(gen.to(a)).map { case (key, value) =>
//      (tag.runtimeClass.getSimpleName :: key, value)
//    }
//  }

}
