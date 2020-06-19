package com.itv.scheduler

import org.quartz.JobExecutionContext

import scala.collection.JavaConverters._

trait QuartzOps {
  implicit class JobExecutionInstances(jobExecutionContext: JobExecutionContext) {
    def jobDataMap: Map[String, String] =
      jobExecutionContext.getJobDetail.getJobDataMap.asScala.mapValues(_.toString).toMap
  }
}
object QuartzOps extends QuartzOps
