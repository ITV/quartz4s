package com.itv.scheduler

import cats.implicits._
import cats.Contravariant

final case class JobData(dataMap: Map[String, String])

trait JobDataEncoder[A] {
  def apply(a: A): JobData
}

object JobDataEncoder {
  implicit val contravariantJobDataEncoder: Contravariant[JobDataEncoder] = new Contravariant[JobDataEncoder] {
    override def contramap[A, B](fa: JobDataEncoder[A])(f: B => A): JobDataEncoder[B] =
      (b: B) => fa(f(b))
  }
}
