package com.itv.scheduler

import cats.laws.discipline.FunctorTests
import munit.DisciplineSuite
import org.scalacheck.Arbitrary._

class JobDecoderLawTests extends DisciplineSuite with Generators {
  checkAll("JobDecoder.FunctorLaws", FunctorTests[JobDecoder].functor[JobWithNesting, Int, String])
}
