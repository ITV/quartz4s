package com.itv.scheduler

import cats.laws.discipline.InvariantTests
import munit.DisciplineSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import cats.Eq
import org.quartz.JobDataMap
import org.scalacheck.Gen.alphaStr

import scala.jdk.CollectionConverters._

class JobCodecLawTests extends DisciplineSuite with Generators with ScalaCheckPropertyChecks {
  forAll(jobWithNestingGen.arbitrary, alphaStr) { case (job, str) =>
    implicit val eqJobEncoder: Eq[JobDataEncoder[JobWithNesting]] = eqJobDataEncoder(job)
    implicit val eqStrEncoder: Eq[JobDataEncoder[String]]         = eqJobDataEncoder(str)

    val jEC = MockJobExecutionContext(new JobDataMap(JobWithNesting.jobCodec.encode(job).dataMap.asJava))
    implicit val eqJobDec: Eq[JobDecoder[JobWithNesting]] = eqJobDecoder[JobWithNesting](jEC)
    implicit val eqJobDecStr: Eq[JobDecoder[String]]      = eqJobDecoder[String](jEC)

    checkAll("JobCodec.InvariantLaws", InvariantTests[JobCodec].invariant[JobWithNesting, Int, String])
  }
}
