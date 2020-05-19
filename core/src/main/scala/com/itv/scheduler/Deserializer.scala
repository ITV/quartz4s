package com.itv.scheduler

import org.quartz.JobExecutionContext

trait Deserializer {
  def apply[A](jobExecutionContext: JobExecutionContext): Throwable Either A
}
