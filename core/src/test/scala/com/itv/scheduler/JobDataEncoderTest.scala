package com.itv.scheduler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JobDataEncoderTest extends AnyFlatSpec with Matchers {
  behavior of "JobDataEncoder"

  it should "encode a sealed trait correctly" in {
    JobDataEncoder[ParentTestJob].encode(ChildObjectJob) shouldBe JobData(Map("type" -> "ChildObjectJob"))
    JobDataEncoder[ParentTestJob].encode(UserJob(UserId("123"))) shouldBe JobData(
      Map("type" -> "UserJob", "userjob.id" -> "123")
    )
  }

  it should "encode a case class correctly where there is nesting" in {
    JobDataEncoder[JobWithNesting].encode(
      JobWithNesting("bob", Some(true), UserJob(UserId("123")), None)
    ) shouldBe JobData(
      Map(
        "jobwithnesting.a"            -> "bob",
        "jobwithnesting.b"            -> "true",
        "jobwithnesting.c.type"       -> "UserJob",
        "jobwithnesting.c.userjob.id" -> "123"
      )
    )
  }
}
