package com.itv.scheduler

import org.scalacheck.{Arbitrary, Cogen}
import scala.deriving.Mirror

trait JobWithNestingGenerators {
  given [P <: Product](using m: Mirror.ProductOf[P], ts: Arbitrary[m.MirroredElemTypes]): Arbitrary[P] =
    Arbitrary(ts.arbitrary.map(m.fromProduct))

  given [P <: Product](using m: Mirror.ProductOf[P], ts: Cogen[m.MirroredElemTypes]): Cogen[P] =
    ts.contramap(Tuple.fromProductTyped)
}
