package com.itv

import cats.effect.concurrent.Deferred

package object scheduler {
  type MessageAcker[F[_], A] = Deferred[F, Either[Throwable, Unit]]
}
