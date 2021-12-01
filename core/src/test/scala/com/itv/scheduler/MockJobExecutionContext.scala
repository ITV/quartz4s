package com.itv.scheduler

import org.quartz.*
import java.util.Date

object MockJobExecutionContext {
  def apply(jobData: JobData): JobExecutionContext =
    new JobExecutionContext {
      def getJobDetail: JobDetail = JobDetailOps.toJobDetail(jobData)

      def getScheduler: Scheduler             = ???
      def getTrigger: Trigger                 = ???
      def getCalendar: Calendar               = ???
      def isRecovering: Boolean               = ???
      def getRecoveringTriggerKey: TriggerKey = ???
      def getRefireCount: Int                 = ???
      def getMergedJobDataMap: JobDataMap     = ???
      def getJobInstance: Job                 = ???
      def getFireTime: Date                   = ???
      def getScheduledFireTime: Date          = ???
      def getPreviousFireTime: Date           = ???
      def getNextFireTime: Date               = ???
      def getFireInstanceId: String           = ???
      def getResult: AnyRef                   = ???
      def setResult(result: Any): Unit        = ???
      def getJobRunTime: Long                 = ???
      def put(key: Any, value: Any): Unit     = ???
      def get(key: Any): AnyRef               = ???
    }
}
