package com.itv.scheduler

import cats.data.Chain
import cats.{Contravariant, Functor, Invariant}

trait JobCodec[A] extends JobDecoder[A] with JobDataEncoder[A]

object JobCodec {
  implicit val invariantInstance: Invariant[JobCodec] = new Invariant[JobCodec] {
    override def imap[A, B](fa: JobCodec[A])(f: A => B)(g: B => A): JobCodec[B] =
      JobCodec.from[B](Functor[JobDecoder].map(fa)(f), Contravariant[JobDataEncoder].contramap(fa)(g))
  }

  def from[A](implicit decodeA: JobDecoder[A], encodeA: JobDataEncoder[A]): JobCodec[A] = new JobCodec[A] {
    override private[scheduler] def read(path: Chain[String], jobData: PartiallyDecodedJobData): Either[Throwable, A] =
      decodeA.read(path, jobData)

    override private[scheduler] def apply(a: A): Map[List[String], String] =
      encodeA.apply(a)
  }

  def apply[A](implicit ev: JobCodec[A]): JobCodec[A] = ev
}
