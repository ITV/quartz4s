package com.itv.scheduler

import com.itv.scheduler.QuartzOps.*
import org.quartz.JobDetail
import org.quartz.impl.JobDetailImpl

trait JobDetailOps {
  def toJobDetail(jobData: JobData): JobDetail = {
    val jobDetail = new JobDetailImpl()
    jobDetail.setJobDataMap(jobData.toJobDataMap)
    jobDetail
  }
}
object JobDetailOps extends JobDetailOps
