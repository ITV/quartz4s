package com.itv.scheduler

import cats.laws.discipline.InvariantTests
import munit.DisciplineSuite
import org.scalacheck.Arbitrary._
import org.scalacheck.ScalacheckShapeless._

class JobCodecLawTests extends DisciplineSuite with Generators {
  checkAll("JobCodec.InvariantLaws", InvariantTests[JobCodec].invariant[JobWithNesting, Int, String])
}
