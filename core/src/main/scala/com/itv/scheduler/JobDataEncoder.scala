package com.itv.scheduler

import cats.Contravariant

trait JobDataEncoder[A] {
  private[scheduler] def apply(a: A): Map[List[String], String]
  def encode(a: A): JobData =
    JobData(apply(a).map { case (keys, value) =>
      (keys.mkString(".").toLowerCase, value)
    })
}

object JobDataEncoder {
  implicit val contravariantJobDataEncoder: Contravariant[JobDataEncoder] = new Contravariant[JobDataEncoder] {
    override def contramap[A, B](fa: JobDataEncoder[A])(f: B => A): JobDataEncoder[B] =
      (b: B) => fa(f(b))
  }

  def instance[A](encodeAsMap: A => Map[List[String], String]): JobDataEncoder[A] = (a: A) => encodeAsMap(a)

  def apply[A](implicit evidence: JobDataEncoder[A]): JobDataEncoder[A] = evidence
}
