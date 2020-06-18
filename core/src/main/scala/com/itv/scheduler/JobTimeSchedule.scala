package com.itv.scheduler

import java.time.Instant

import org.quartz.CronExpression

import scala.concurrent.duration.FiniteDuration

sealed trait JobTimeSchedule
final case class CronScheduledJob(cronExpression: CronExpression) extends JobTimeSchedule
final case class JobScheduledAt(runTime: Instant)                 extends JobTimeSchedule
final case class SimpleJob(repeatEvery: FiniteDuration)           extends JobTimeSchedule
