package com.itv.scheduler

import org.quartz.{JobDataMap, JobDetail, JobExecutionContext}
import org.scalamock.scalatest.MockFactory

object MockJobExecutionContext extends MockFactory {
  def apply(jobDataMap: JobDataMap): JobExecutionContext = {
    val jEC       = stub[JobExecutionContext]
    val jobDetail = stub[JobDetail]
    (jEC.getJobDetail _).when().returns(jobDetail)
    (jobDetail.getJobDataMap _).when().returns(jobDataMap)
    jEC
  }
}
