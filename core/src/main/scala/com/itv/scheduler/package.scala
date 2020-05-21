package com.itv

package object scheduler {
  type MessageScheduler[F[_], J, A] = TaskScheduler[F, J] with ScheduledMessageReceiver[F, A]
}
