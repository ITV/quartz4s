package com.itv.scheduler

import org.scalacheck._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.JavaConverters._

class Fs2QuartzConfigTest extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  behavior of "Fs2QuartzConfig"

  val sizedStringGen =
    Gen.chooseNum[Int](8, 16).flatMap(num => Gen.buildableOfN[String, Char](num, Gen.asciiPrintableChar))

  it should "load config correctly" in {
    forAll(
      Gen.posNum[Int],
      Gen.posNum[Int],
      sizedStringGen,
      sizedStringGen,
      sizedStringGen,
    ) {
      case (threadCount, maxConnections, jdbcUrl, username, password) =>
        val quartzConfig = Fs2QuartzConfig(
          JobStoreConfig(
            driverDelegateClass = classOf[org.quartz.impl.jdbcjobstore.PostgreSQLDelegate],
          ),
          ThreadPoolConfig(threadCount),
          DataSourceConfig(
            driverClass = classOf[org.postgresql.Driver],
            jdbcUrl = jdbcUrl,
            username = username,
            password = password,
            maxConnections = maxConnections,
          )
        )

        val expectedProperties: Map[String, String] = Map(
          "org.quartz.jobStore.isClustered"         -> "true",
          "org.quartz.jobStore.driverDelegateClass" -> "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate",
          "org.quartz.threadPool.threadCount"       -> threadCount.toString,
          "org.quartz.jobStore.class"               -> "org.quartz.impl.jdbcjobstore.JobStoreTX",
          "org.quartz.jobStore.dataSource"          -> "ds",
          "org.quartz.dataSource.ds.provider"       -> "hikaricp",
          "org.quartz.dataSource.ds.driver"         -> "org.postgresql.Driver",
          "org.quartz.dataSource.ds.URL"            -> jdbcUrl,
          "org.quartz.dataSource.ds.user"           -> username,
          "org.quartz.dataSource.ds.password"       -> password,
          "org.quartz.dataSource.ds.maxConnections" -> maxConnections.toString,
        )
        quartzConfig.toQuartzProperties.properties.asScala.toMap should contain theSameElementsAs expectedProperties
    }
  }
}
