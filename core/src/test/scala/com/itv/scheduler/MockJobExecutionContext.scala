package com.itv.scheduler

import org.quartz.*
import org.quartz.impl.*
import org.quartz.spi.TriggerFiredBundle

object MockJobExecutionContext {
  def apply(jobData: JobData): JobExecutionContext = {
    val jobDetail = JobDetailOps.toJobDetail(jobData)
    val job = new Job {
      override def execute(context: JobExecutionContext): Unit = ()
    }
    val firedBundle = new TriggerFiredBundle(jobDetail, null, null, false, null, null, null, null)
    new JobExecutionContextImpl(null, firedBundle, job)
  }
}
