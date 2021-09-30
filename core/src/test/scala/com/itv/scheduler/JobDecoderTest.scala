package com.itv.scheduler

import org.quartz.{JobDetail, JobExecutionContext}
import cats.implicits._
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import QuartzOps._

class JobDecoderTest extends AnyFlatSpec with Matchers with MockFactory {
  behavior of "JobDecoder"

  def decodeMap[A: JobDecoder](map: Map[String, String]): A = {
    val jobExecutionContext = stub[JobExecutionContext]
    val jobDetail           = stub[JobDetail]
    (jobExecutionContext.getJobDetail _).when().returns(jobDetail)
    (jobDetail.getJobDataMap _).when().returns(JobData(map).toJobDataMap)
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
