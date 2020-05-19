package com.itv.scheduler

import cats.implicits._
import extruder.core._
import extruder.map._
import org.quartz.JobKey

trait JobData {
  def key: JobKey
  def dataMap: Map[String, String]
}

trait JobDataEncoder[A] {
  def apply(a: A): JobData
}

object JobDataEncoder {
  def forKey[A](jobKey: JobKey)(implicit multiShow: MultiShow[A]): JobDataEncoder[A] =
    (a: A) =>
      new JobData {
        override def key: JobKey                  = jobKey
        override def dataMap: Map[String, String] = encode(a)
      }

}

//sealed trait JobData extends Job with StrictLogging {
//  def name: String
//  def group: Option[String]
//  def triggerKey: TriggerKey
//  def jobDataMap: JobDataMap
//
//  protected def executeJob(): IO[Unit]
//
//  protected def executeAndLog[A](operationName: String, f: IO[A]): IO[Unit] = {
//    logger.info(s"Running $operationName ...")
//    f.flatMap(results => IO(logger.info(s"Ran $operationName: $results")))
//      .onError {
//        case ex =>
//          IO(logger.error(s"Error occurred running $operationName: ${ex.toString}"))
//      }
//  }
//
//  override def execute(context: JobExecutionContext): Unit =
//    executeJob().unsafeRunSync()
//}
//
//object JobData {
//  val emptyJobDataMap: JobDataMap = new JobDataMap()
//  val entitlementsGroup           = Some("entitlements")
//}
//
//final class CheckExpiringReceipts() extends JobData {
//  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
//  @BeanProperty var sendSubscriptionsForRenewal: IO[Ior[Int, Int]] = _
//
//  override val name: String           = "check-expiring-receipts"
//  override val group: Option[String]  = JobData.entitlementsGroup
//  override val triggerKey             = new TriggerKey("everyHour")
//  override val jobDataMap: JobDataMap = JobData.emptyJobDataMap
//
//  protected override def executeJob(): IO[Unit] =
//    executeAndLog("renewing subscriptions", sendSubscriptionsForRenewal)
//}
//
//final class CheckOlderExpiringReceipts() extends JobData {
//  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
//  @BeanProperty var sendOlderSubscriptionsForRenewal: IO[Ior[Int, Int]] = _
//
//  override val name: String           = "check-older-expiring-receipts"
//  override val group: Option[String]  = JobData.entitlementsGroup
//  override val triggerKey             = new TriggerKey("check-older-expiring-receipts")
//  override val jobDataMap: JobDataMap = JobData.emptyJobDataMap
//
//  protected override def executeJob(): IO[Unit] =
//    executeAndLog("renewing older subscriptions", sendOlderSubscriptionsForRenewal)
//}
//
//final class SendUnverifiedReceipts() extends JobData {
//  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
//  @BeanProperty var sendUnverifiedReceipts: IO[Ior[Int, Int]] = _
//
//  override val name: String           = "send-unverified-receipts"
//  override val group: Option[String]  = JobData.entitlementsGroup
//  override val triggerKey             = new TriggerKey("send-unverified-trigger")
//  override val jobDataMap: JobDataMap = JobData.emptyJobDataMap
//
//  protected override def executeJob(): IO[Unit] =
//    executeAndLog("unverified receipts", sendUnverifiedReceipts)
//}
//
//@SuppressWarnings(Array("org.wartremover.warts.Var"))
//final case class CreateOptimisticEntitlement(@BeanProperty var itvId: String) extends JobData {
//  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
//  @BeanProperty var sendOptimisticEntitlement: ITVId => IO[Either[DomainError, SentEntitlementsSuccess]] = _
//
//  // because java - Quartz needs a no-arg constructor, and will then call the setter using values from the task data map
//  @SuppressWarnings(Array("org.wartremover.warts.Null"))
//  def this() = this(null: String)
//
//  def this(itvId: ITVId) = this(itvId.id)
//
//  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
//  private lazy val getItvIdOrFail =
//    Option(itvId).getOrElse(throw new IllegalStateException("ITVId should have been set"))
//
//  override lazy val name: String = "create-optimistic-entitlement-" + getItvIdOrFail
//
//  override val group: Option[String]       = JobData.entitlementsGroup
//  override lazy val triggerKey: TriggerKey = new TriggerKey("optimistic-entitlement-trigger-" + getItvIdOrFail)
//  override def jobDataMap: JobDataMap = {
//    val jobDataMap = new JobDataMap()
//    jobDataMap.put("itvId", getItvIdOrFail)
//    jobDataMap
//  }
//
//  @SuppressWarnings(Array("org.wartremover.warts.JavaSerializable"))
//  protected override def executeJob(): IO[Unit] = {
//    logger.info(s"Creating optimistic entitlement for $itvId")
//    sendOptimisticEntitlement(ITVId(itvId))
//      .flatMap(result => IO(logger.info(s"Created optimistic entitlement for $itvId: $result")))
//      .onError {
//        case ex =>
//          IO(logger.error(s"Error creating optimistic entitlement for $itvId: $ex"))
//      }
//  }
//}
//
//@SuppressWarnings(Array("org.wartremover.warts.Var"))
//final class DeactivateLapsedUsers() extends JobData {
//  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
//  @BeanProperty var deactivateLapsedUsersBeforeTime: IO[Int] = _
//
//  override def name: String           = "deactivate-lapsed-users"
//  override def group: Option[String]  = JobData.entitlementsGroup
//  override def triggerKey: TriggerKey = new TriggerKey("deactivate-lapsed-users-trigger")
//  override def jobDataMap: JobDataMap = JobData.emptyJobDataMap
//
//  override protected def executeJob(): IO[Unit] =
//    executeAndLog("mark users as inactive", deactivateLapsedUsersBeforeTime)
//}
