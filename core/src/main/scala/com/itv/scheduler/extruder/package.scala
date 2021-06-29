package com.itv.scheduler

import cats.data.Chain
import shapeless.Lazy

package object extruder {
  object semiauto extends DerivedDecoders with DerivedEncoders {
    def deriveJobDecoder[A](implicit ev: Lazy[DerivedJobDecoder[A]]): JobDecoder[A]         = ev.value
    def deriveJobEncoder[A](implicit ev: Lazy[DerivedJobDataEncoder[A]]): JobDataEncoder[A] = ev.value
    def deriveJobCodec[A](implicit ev1: Lazy[DerivedJobDecoder[A]], ev2: Lazy[DerivedJobDataEncoder[A]]): JobCodec[A] =
      new JobCodec[A] {
        def read(path: Chain[String], jobData: PartiallyDecodedJobData): Either[Throwable, A] =
          ev1.value.read(path, jobData)
        def apply(a: A): Map[List[String], String] = ev2.value.apply(a)
      }
  }

  object derivingImplicits extends DerivedDecoders with DerivedEncoders

  object primitives extends PrimitiveEncoders with PrimitiveDecoders
}
