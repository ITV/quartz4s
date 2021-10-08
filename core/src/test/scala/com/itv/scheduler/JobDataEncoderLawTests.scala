package com.itv.scheduler

import cats.Eq
import cats.laws.discipline.ContravariantTests
import munit.DisciplineSuite
import org.scalacheck.Arbitrary._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen._

class JobDataEncoderLawTests extends DisciplineSuite with Generators with ScalaCheckPropertyChecks {
  forAll(jobWithNestingGen.arbitrary, alphaStr) { case (job, str) =>
    implicit val eqJobEncoder: Eq[JobDataEncoder[JobWithNesting]] = eqJobDataEncoder(job)
    implicit val eqStrEncoder: Eq[JobDataEncoder[String]]         = eqJobDataEncoder(str)
    checkAll(
      "JobDataEncoder.ContravariantLaws",
      ContravariantTests[JobDataEncoder].contravariant[JobWithNesting, Int, String]
    )
  }
}
