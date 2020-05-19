package com.itv.scheduler

import org.quartz.JobExecutionContext

trait JobDecoder[A] {
  def apply(jobExecutionContext: JobExecutionContext): Throwable Either A
}

object JobDecoder {
  def apply[A](implicit evidence: JobDecoder[A]): JobDecoder[A] = evidence
}
