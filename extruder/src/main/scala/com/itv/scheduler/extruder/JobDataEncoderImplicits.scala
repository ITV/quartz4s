package com.itv.scheduler.extruder

import extruder.core._
import extruder.map._
import com.itv.scheduler.{JobData, JobDataEncoder}
import org.quartz.JobKey

trait JobDataEncoderImplicits {
  implicit class JobDataEncoderW(encoderType: JobDataEncoder.type) {
    def forKey[A](jobKey: JobKey)(implicit multiShow: MultiShow[A]): JobDataEncoder[A] =
      (a: A) =>
        new JobData {
          override def key: JobKey                  = jobKey
          override def dataMap: Map[String, String] = encode(a)
        }
  }
}
