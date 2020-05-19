package com.itv.scheduler.extruder

import com.itv.scheduler.{JobData, JobDataEncoder}
import extruder.core._
import extruder.map._

trait JobDataEncoderImplicits {
  implicit def multiShowToJobDataEncoder[A](implicit show: Show[A]): JobDataEncoder[A] =
    (a: A) => JobData(encode(a))

//  implicit class JobDataEncoderW(encoderType: JobDataEncoder.type) {
//    def forKey[A: MultiShow](jobKey: JobKey): JobDataEncoder[A] =
//      (a: A) => JobData(encode(a))
//  }
//
//  implicit class JobDataEncoderW(encoderType: JobDataEncoder.type) {
//    def forKey(jobKey: JobKey): JobDataEncoderPartiallyApplied =
//      new JobDataEncoderPartiallyApplied(jobKey)
//  }
//
//  implicit class JobDataEncoderPartiallyApplied(jobKey: JobKey) {
//    def apply[A](implicit multiShow: MultiShow[A]): JobDataEncoder[A] = { (a: A) =>
//      new JobData {
//        override def key: JobKey                  = jobKey
//        override def dataMap: Map[String, String] = encode(a)
//      }
//    }
//  }
}
