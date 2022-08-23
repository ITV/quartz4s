package com.itv.scheduler

import org.scalacheck.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class Quartz4sConfigTest extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  behavior of "Quartz4sConfig"

  val sizedStringGen =
    Gen.chooseNum[Int](8, 16).flatMap(num => Gen.buildableOfN[String, Char](num, Gen.asciiPrintableChar))

  it should "load config correctly" in {
    forAll(
      Gen.posNum[Int],
      Gen.posNum[Int],
      sizedStringGen,
      sizedStringGen,
      sizedStringGen,
    ) { case (threadCount, maxConnections, jdbcUrl, username, password) =>
      val quartzConfig = Quartz4sConfig(
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
      quartzConfig.defaultProperties should contain theSameElementsAs expectedProperties
    }
  }

  it should "set additional properties overriding default props with a matching key" in {
    forAll(
      Gen.posNum[Int],
      Gen.posNum[Int],
      sizedStringGen,
      sizedStringGen,
      sizedStringGen,
    ) { case (threadCount, maxConnections, jdbcUrl, username, password) =>
      val quartzConfig = Quartz4sConfig(
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

      val additionalProperties: Map[String, String] = Map(
        "org.quartz.jobStore.isClustered" -> "false"
      )

      quartzConfig.toQuartzProperties(additionalProperties).properties.get("org.quartz.jobStore.isClustered") should be ("false")
    }
  }
}
