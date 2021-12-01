package com.itv.scheduler.extruder

import cats.data.Chain
import cats.syntax.all.*
import com.itv.scheduler.extruder.Derivation.{summonDecoders, summonLabels}
import com.itv.scheduler.*

import scala.reflect.ClassTag
import scala.deriving.Mirror
import scala.compiletime.{constValue, erasedValue, error, summonFrom, summonInline}

private[extruder] trait DerivedJobDecoder[A] extends DerivedInstance[A] with JobDecoder[A] {
  private[extruder] type Maybe[A] = Either[Throwable, A]

  protected[this] def elemDecoders: Array[JobDecoder[?]]

  final def readWith(index: Int)(path: Chain[String], jobData: PartiallyDecodedJobData): Maybe[AnyRef] =
    elemDecoders(index).asInstanceOf[JobDecoder[AnyRef]].read(path, jobData)

  final def resultIterator(
      path: Chain[String],
      fields: Array[String],
      clazz: String,
      jobData: PartiallyDecodedJobData
  ): Iterator[Maybe[AnyRef]] =
    new Iterator[Maybe[AnyRef]] {
      private[this] var i: Int = 0
      def hasNext: Boolean     = i < elemCount
      def next: Maybe[AnyRef] = {
        val result = readWith(i)(path :+ clazz :+ fields(i), jobData)
        i += 1
        result
      }
    }
}

private[extruder] trait DerivedJobDataEncoder[A] extends DerivedInstance[A] with JobDataEncoder[A] {
  protected[this] def elemEncoders: Array[JobDataEncoder[?]]

  final def encodeWith(index: Int)(value: Any): Map[List[String], String] =
    elemEncoders(index).asInstanceOf[JobDataEncoder[Any]].apply(value).map { case (key, value) =>
      (elemLabels(index) :: key, value)
    }

  final def encodedProduct(value: Product): Map[List[String], String] =
    value.productIterator.zipWithIndex.flatMap { case (v, idx) =>
      encodeWith(idx)(v)
    }.toMap
}

private[scheduler] trait DerivedInstance[A](
    final val name: String,
    final val elemLabels: Array[String]
) {
  final def elemCount: Int = elemLabels.length
  final def findLabel(name: String): Int = {
    var i = 0
    while i < elemCount do {
      if elemLabels(i).equalsIgnoreCase(name) then return i
      i += 1
    }
    return -1
  }
}

private[scheduler] trait DerivedEncoders extends PrimitiveEncoders {
  inline final def derivedEncoder[A](using
      inline A: Mirror.Of[A],
      inline neOpt: A <:!< Option[?],
      inline neEither: A <:!< Either[?, ?]
  ): DerivedJobDataEncoder[A] = new DerivedJobDataEncoder[A]
    with DerivedInstance[A](
      constValue[A.MirroredLabel].toLowerCase,
      Derivation.summonLabels[A.MirroredElemLabels].map(_.toLowerCase)
    ) {
    protected[this] def elemEncoders: Array[JobDataEncoder[?]] = Derivation.summonEncoders[A.MirroredElemTypes]

    private[scheduler] def apply(a: A): Map[List[String], String] = inline A match {
      case m: Mirror.ProductOf[A] =>
        encodedProduct(a.asInstanceOf[Product]).map { case (key, value) =>
          (name :: key, value)
        }
      case m: Mirror.SumOf[A] => encodeWith(m.ordinal(a))(a) + (List("type") -> name)
    }
  }
}

private[scheduler] trait DerivedDecoders extends PrimitiveDecoders {
  inline final def derivedDecoder[A](using
      inline A: Mirror.Of[A],
      inline neOpt: A <:!< Option[?],
      inline neEither: A <:!< Either[?, ?]
  ): DerivedJobDecoder[A] = new DerivedJobDecoder[A]
    with DerivedInstance[A](
      constValue[A.MirroredLabel].toLowerCase,
      Derivation.summonLabels[A.MirroredElemLabels].map(_.toLowerCase)
    ) {
    protected[this] lazy val elemDecoders: Array[JobDecoder[?]] = summonDecoders[A.MirroredElemTypes]

    private[scheduler] def read(path: Chain[String], jobData: PartiallyDecodedJobData) = inline A match {
      case m: Mirror.ProductOf[A] =>
        val iter                       = resultIterator(path, elemLabels, name, jobData)
        val res                        = new Array[AnyRef](elemCount)
        var failed: Left[Throwable, ?] = null
        var i: Int                     = 0

        while iter.hasNext && (failed eq null) do {
          iter.next match {
            case Right(value) => res(i) = value
            case l @ Left(_)  => failed = l
          }
          i += 1
        }

        if failed eq null then {
          Right(m.fromProduct(Tuple.fromArray(res)))
        } else {
          failed.asInstanceOf[Left[Throwable, A]]
        }
      case m: Mirror.SumOf[A] =>
        jobData.value
          .collectFirst[String] {
            case (p, value) if p === path :+ "type" =>
              value
          }
          .map(findLabel)
          .getOrElse(-1) match {
          case -1  => Left(new RuntimeException("Could not find specified implementation of sealed type"))
          case idx => readWith(idx)(path, jobData).asInstanceOf[Maybe[A]]
        }
    }
  }
}

object Derivation {

  inline final def summonLabels[T <: Tuple]: Array[String]              = summonLabelsRec[T].toArray
  inline final def summonDecoders[T <: Tuple]: Array[JobDecoder[?]]     = summonDecodersRec[T].toArray
  inline final def summonEncoders[T <: Tuple]: Array[JobDataEncoder[?]] = summonEncodersRec[T].toArray

  inline final def summonDecoder[A]: JobDecoder[A] = summonFrom {
    case decodeA: JobDecoder[A] => decodeA
    case _: Mirror.Of[A]        => deriving.derivedDecoder[A]
  }

  inline final def summonEncoder[A]: JobDataEncoder[A] = summonFrom {
    case encodeA: JobDataEncoder[A] => encodeA
    case _: Mirror.Of[A]            => deriving.derivedEncoder[A]
  }

  inline final def summonLabelsRec[T <: Tuple]: List[String] = inline erasedValue[T] match {
    case _: EmptyTuple => Nil
    case _: (t *: ts)  => constValue[t].asInstanceOf[String] :: summonLabelsRec[ts]
  }

  inline final def summonDecodersRec[T <: Tuple]: List[JobDecoder[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonDecoder[t] :: summonDecodersRec[ts]
    }

  inline final def summonEncodersRec[T <: Tuple]: List[JobDataEncoder[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonEncoder[t] :: summonEncodersRec[ts]
    }
}

@scala.annotation.implicitNotFound("${A} must not be a subtype of ${B}")
trait <:!<[A, B] extends Serializable

object <:!< {
  given nsub[A, B]: <:!<[A, B]            = new <:!<[A, B] {}
  given nsubAmbig1[A, B >: A]: <:!<[A, B] = sys.error("Unexpected invocation")
  given nsubAmbig2[A, B >: A]: <:!<[A, B] = sys.error("Unexpected invocation")
}
