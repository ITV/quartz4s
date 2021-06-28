package com.itv.scheduler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JobDataEncoderTest extends AnyFlatSpec with Matchers {
  behavior of "JobDataEncoder"

  it should "encode a sealed trait correctly" in {
    val encoder = JobDataEncoder[ParentTestJob]
    encoder.toJobData(ChildObjectJob) shouldBe JobData(Map("type" -> "ChildObjectJob"))
    encoder.toJobData(UserJob("123")) shouldBe JobData(Map("type" -> "UserJob", "userjob.id" -> "123"))
  }

  it should "encode a case class correctly where there is nesting" in {
    val encoder = JobDataEncoder[JobWithNesting]
    encoder.toJobData(JobWithNesting("bob", Some(true), UserJob("123"))) shouldBe JobData(
      Map(
        "jobwithnesting.a"            -> "bob",
        "jobwithnesting.b"            -> "true",
        "jobwithnesting.c.type"       -> "UserJob",
        "jobwithnesting.c.userjob.id" -> "123"
      )
    )
  }
}

final case class JobWithNesting(a: String, b: Option[Boolean], c: ParentTestJob)
