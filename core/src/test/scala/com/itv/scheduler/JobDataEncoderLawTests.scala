//package com.itv.scheduler
//
//import cats.Eq
//import cats.laws.discipline.ContravariantTests
//import munit.DisciplineSuite
//import org.scalacheck.Arbitrary.*
//import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
//import org.scalacheck.Gen.*
//
//class JobDataEncoderLawTests
//    extends DisciplineSuite
//    with Generators
//    with JobWithNestingGenerators
//    with ScalaCheckPropertyChecks {
//  forAll(arbitrary[JobWithNesting], alphaStr) { case (job, str) =>
//    implicit val eqJobEncoder: Eq[JobDataEncoder[JobWithNesting]] = eqJobDataEncoder(job)
//    implicit val eqStrEncoder: Eq[JobDataEncoder[String]]         = eqJobDataEncoder(str)
//    checkAll(
//      "JobDataEncoder.ContravariantLaws",
//      ContravariantTests[JobDataEncoder].contravariant[JobWithNesting, Int, String]
//    )
//  }
//}
