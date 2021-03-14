package com.itv.scheduler

import org.quartz.JobDataMap

import scala.jdk.CollectionConverters._

trait QuartzOps {
  implicit class JobDataMapOps(jobDataMap: JobDataMap) {
    def toMap: Map[String, String] = jobDataMap.asScala.view.mapValues(_.toString).toMap
  }

  implicit class JobDataOps(jobData: JobData) {
    def toJobDataMap: JobDataMap = new JobDataMap(jobData.dataMap.asJava)
  }
}
object QuartzOps extends QuartzOps
