package com.itv

import cats.effect.kernel.Deferred

package object scheduler {
  type MessageAcker[F[_]] = Deferred[F, Either[Throwable, Unit]]
}
