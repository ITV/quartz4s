package com.itv.scheduler

import cats.implicits._
import com.itv.scheduler.QuartzOps._
import org.quartz.JobExecutionContext

sealed trait ParentTestJob
case object ChildObjectJob     extends ParentTestJob
case class UserJob(id: String) extends ParentTestJob

object ParentTestJob {
  implicit val jobDataEncoder: JobDataEncoder[ParentTestJob] = {
    case ChildObjectJob => JobData(Map("type" -> "child"))
    case UserJob(id)    => JobData(Map("type" -> "user", "id" -> id))
  }
  implicit val jobDecoder: JobDecoder[ParentTestJob] = new JobDecoder[ParentTestJob] {
    override def apply(jobExecutionContext: JobExecutionContext): Either[Throwable, ParentTestJob] =
      Either.catchNonFatal(jobExecutionContext.getJobDetail.getJobDataMap.toMap).flatMap { jobDataMap =>
        findField(jobDataMap, "type").flatMap {
          case "child" => Right(ChildObjectJob)
          case "user"  => findField(jobDataMap, "id").map(UserJob)
          case other   => Left(new IllegalArgumentException(s"Illegal job type $other"))
        }
      }
  }

  private def findField(jobDataMap: Map[String, String], fieldName: String): Either[Throwable, String] =
    jobDataMap.get(fieldName).toRight(new IllegalArgumentException(s"Could not find field `$fieldName`"))
}
