package com.itv.scheduler

import cats.implicits._
import org.quartz._

import scala.util.Either

abstract class PublishCallbackJob extends Job {
  def handleMessage: JobExecutionContext => Either[Throwable, Unit]

  override def execute(context: JobExecutionContext): Unit =
    handleMessage(context).valueOr(throw _)
}
