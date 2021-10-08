package com.itv.scheduler

import cats.Eq
import cats.implicits.catsStdEqForEither
import cats.laws.discipline.FunctorTests
import munit.DisciplineSuite
import org.quartz.JobDataMap
import org.scalacheck.Arbitrary._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.jdk.CollectionConverters._

class JobDecoderLawTests extends DisciplineSuite with Generators with ScalaCheckPropertyChecks {
  forAll(jobWithNestingGen.arbitrary) { job =>
    val jEC = MockJobExecutionContext(new JobDataMap(JobWithNesting.jobCodec.encode(job).dataMap.asJava))
    implicit val eqJobDec: Eq[JobDecoder[JobWithNesting]] = eqJobDecoder[JobWithNesting](jEC)
    implicit val eqJobDecStr: Eq[JobDecoder[String]]      = eqJobDecoder[String](jEC)

    checkAll("JobDecoder.FunctorLaws", FunctorTests[JobDecoder].functor[JobWithNesting, Int, String])
  }

}
