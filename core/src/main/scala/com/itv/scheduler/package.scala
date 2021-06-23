package com.itv

import cats.effect.Deferred

package object scheduler {
  type MessageAcker[F[_]] = Deferred[F, Either[Throwable, Unit]]
}
