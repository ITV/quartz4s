//package com.itv.scheduler
//
//import cats.laws.discipline.InvariantTests
//import munit.DisciplineSuite
//import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
//import cats.Eq
//import org.scalacheck.Arbitrary.arbitrary
//import org.scalacheck.Gen.alphaStr
//import org.scalacheck.Shapeless.derivedArbitrary
//
//class JobCodecLawTests
//    extends DisciplineSuite
//    with Generators
//    with JobWithNestingGenerators
//    with ScalaCheckPropertyChecks {
//  forAll(arbitrary[JobWithNesting], alphaStr) { case (job, str) =>
//    implicit val eqJobEncoder: Eq[JobDataEncoder[JobWithNesting]] = eqJobDataEncoder(job)
//    implicit val eqStrEncoder: Eq[JobDataEncoder[String]]         = eqJobDataEncoder(str)
//
//    val jEC                                               = MockJobExecutionContext(JobWithNesting.jobCodec.encode(job))
//    implicit val eqJobDec: Eq[JobDecoder[JobWithNesting]] = eqJobDecoder[JobWithNesting](jEC)
//    implicit val eqJobDecStr: Eq[JobDecoder[String]]      = eqJobDecoder[String](jEC)
//
//    checkAll("JobCodec.InvariantLaws", InvariantTests[JobCodec].invariant[JobWithNesting, Int, String])
//  }
//}
