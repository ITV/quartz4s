package com.itv.scheduler

import org.quartz.JobExecutionContext

trait JobDecoder[A] {
  def apply(jobExecutionContext: JobExecutionContext): Throwable Either A
}
