package com.itv.scheduler

import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.derive.*
import org.scalacheck.ScalacheckShapeless.*

trait JobWithNestingGenerators {
  implicit val jobWithNestingGen: Arbitrary[JobWithNesting] =
    MkArbitrary[JobWithNesting].arbitrary

  implicit val jobWithNestingCogen: Cogen[JobWithNesting] =
    MkCogen[JobWithNesting].cogen
}
