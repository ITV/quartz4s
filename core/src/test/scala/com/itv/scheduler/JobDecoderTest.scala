package com.itv.scheduler

import org.quartz.{JobDetail, JobExecutionContext}
import cats.implicits._
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import QuartzOps._

class JobDecoderTest extends AnyFlatSpec with Matchers with MockFactory {
  behavior of "JobDecoder"

  def decodeMap[A](decoder: JobDecoder[A])(map: Map[String, String]): A = {
    val jobExecutionContext = stub[JobExecutionContext]
    val jobDetail           = stub[JobDetail]
    (jobExecutionContext.getJobDetail _).when().returns(jobDetail)
    (jobDetail.getJobDataMap _).when().returns(JobData(map).toJobDataMap)
    decoder.apply(jobExecutionContext).valueOr(error => fail(s"Could not decode map due to: $error"))
  }

  it should "decode values of a sealed trait correctly" in {
    val decoder: JobDecoder[ParentTestJob] = JobDecoder[ParentTestJob]

    decodeMap(decoder)(Map("type" -> "ChildObjectJob")) shouldBe ChildObjectJob
    decodeMap(decoder)(Map("type" -> "UserJob", "userjob.id" -> "123")) shouldBe UserJob("123")
  }

  it should "decode case class correctly where there is nesting" in {
    val decoder: JobDecoder[JobWithNesting] = JobDecoder[JobWithNesting]
    decodeMap(decoder)(
      Map(
        "jobwithnesting.a"            -> "bob",
        "jobwithnesting.b"            -> "true",
        "jobwithnesting.c.type"       -> "UserJob",
        "jobwithnesting.c.userjob.id" -> "123"
      )
    ) shouldBe JobWithNesting("bob", Some(true), UserJob("123"), None)
  }

}
