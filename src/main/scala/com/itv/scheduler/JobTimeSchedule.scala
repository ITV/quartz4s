package com.itv.scheduler

import java.time.Instant

import org.quartz.CronExpression

sealed trait JobTimeSchedule
final case class CronScheduledJob(cronExpression: CronExpression) extends JobTimeSchedule
final case class JobScheduledAt(runTime: Instant)                 extends JobTimeSchedule
