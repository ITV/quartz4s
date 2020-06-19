package com.itv.scheduler.extruder

import com.itv.scheduler._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JobDataEncoderTest extends AnyFlatSpec with Matchers {
  behavior of "JobDataEncoder"

  val encoder: JobDataEncoder[ParentTestJob] = ParentTestJob.jobDataEncoder

  it should "encode values correctly" in {
    encoder(ChildObjectJob) shouldBe JobData(Map("type" -> "ChildObjectJob"))
    encoder(UserJob("123")) shouldBe JobData(Map("type" -> "UserJob", "userjob.id" -> "123"))
  }
}
