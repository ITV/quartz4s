package com.itv.scheduler

import cats.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JobDecoderTest extends AnyFlatSpec with Matchers {
  behavior of "JobDecoder"

  def decodeMap[A: JobDecoder](map: Map[String, String]): A = {
    val jobExecutionContext = MockJobExecutionContext(JobData(map))
    JobDecoder[A].decode(jobExecutionContext).valueOr(error => fail(s"Could not decode map due to: $error"))
  }

  it should "decode values of a sealed trait correctly" in {
    decodeMap[ParentTestJob](Map("type" -> "ChildObjectJob")) shouldBe ChildObjectJob
    decodeMap[ParentTestJob](Map("type" -> "UserJob", "userjob.id" -> "123")) shouldBe UserJob(UserId("123"))
  }

  it should "decode case class correctly where there is nesting" in {
    decodeMap[JobWithNesting](
      Map(
        "jobwithnesting.a"            -> "bob",
        "jobwithnesting.b"            -> "true",
        "jobwithnesting.c.type"       -> "UserJob",
        "jobwithnesting.c.userjob.id" -> "123"
      )
    ) shouldBe JobWithNesting("bob", Some(true), UserJob(UserId("123")), None)
  }

}
