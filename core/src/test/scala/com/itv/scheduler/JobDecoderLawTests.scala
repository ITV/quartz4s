//package com.itv.scheduler
//
//import cats.Eq
//import cats.implicits.catsStdEqForEither
//import cats.laws.discipline.FunctorTests
//import munit.DisciplineSuite
//import org.scalacheck.Arbitrary.*
//import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
//
//class JobDecoderLawTests
//    extends DisciplineSuite
//    with Generators
//    with JobWithNestingGenerators
//    with ScalaCheckPropertyChecks {
//  forAll(arbitrary[JobWithNesting]) { job =>
//    val jEC                                               = MockJobExecutionContext(JobWithNesting.jobCodec.encode(job))
//    implicit val eqJobDec: Eq[JobDecoder[JobWithNesting]] = eqJobDecoder[JobWithNesting](jEC)
//    implicit val eqJobDecStr: Eq[JobDecoder[String]]      = eqJobDecoder[String](jEC)
//
//    checkAll("JobDecoder.FunctorLaws", FunctorTests[JobDecoder].functor[JobWithNesting, Int, String])
//  }
//
//}
