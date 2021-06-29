package com.itv.scheduler

trait JobCodec[A] extends JobDecoder[A] with JobDataEncoder[A]
