package com.itv.scheduler

import org.quartz.{JobDataMap, JobKey}

import scala.collection.JavaConverters._

trait QuartzOps {
  implicit class JobDataMapOps(jobDataMap: JobDataMap) {
    def toMap: Map[String, String] = jobDataMap.asScala.mapValues(_.toString).toMap
  }

  implicit class JobDataOps(jobData: JobData) {
    def toJobDataMap: JobDataMap = new JobDataMap(jobData.dataMap.asJava)
  }

  implicit class JobKeySetOps(keys: java.util.Set[JobKey]) {
    def toList: List[JobKey] = keys.asScala.toList
  }
}
object QuartzOps extends QuartzOps
