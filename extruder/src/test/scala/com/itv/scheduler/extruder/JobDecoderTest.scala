package com.itv.scheduler.extruder

import com.itv.scheduler._
import cats.implicits._
import org.quartz.{JobDataMap, JobDetail, JobExecutionContext}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalamock.scalatest.MockFactory

import scala.collection.JavaConverters._

class JobDecoderTest extends AnyFlatSpec with Matchers with MockFactory {
  behavior of "JobDecoder"

  val decoder: JobDecoder[ParentTestJob] = ParentTestJob.jobDecoder
  def decodeMap(map: Map[String, String]): ParentTestJob = {
    val jobExecutionContext = stub[JobExecutionContext]
    val jobDetail           = stub[JobDetail]
    (jobExecutionContext.getJobDetail _).when().returns(jobDetail)
    (jobDetail.getJobDataMap _).when().returns(new JobDataMap(map.asJava))
    decoder(jobExecutionContext).valueOr(error => fail(s"Could not decode map due to: $error"))
  }

  it should "decode values correctly" in {
    decodeMap(Map("type" -> "ChildObjectJob")) shouldBe ChildObjectJob
    decodeMap(Map("type" -> "UserJob", "userjob.id" -> "123")) shouldBe UserJob("123")
  }
}
