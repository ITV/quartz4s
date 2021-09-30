package com.itv.scheduler

import cats.laws.discipline.ContravariantTests
import munit.DisciplineSuite
import org.scalacheck.Arbitrary._

class JobDataEncoderLawTests extends DisciplineSuite with Generators {
  checkAll(
    "JobDataEncoder.ContravariantLaws",
    ContravariantTests[JobDataEncoder].contravariant[JobWithNesting, Int, String]
  )
}
