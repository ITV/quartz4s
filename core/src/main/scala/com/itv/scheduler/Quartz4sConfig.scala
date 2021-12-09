package com.itv.scheduler

import java.util.Properties

import org.quartz.impl.StdSchedulerFactory.*
import org.quartz.utils.PoolingConnectionProvider.*

final case class JobStoreConfig(
    isClustered: Boolean = JobStoreConfig.Defaults.isClustered,
    jobStoreClass: Class[?] = JobStoreConfig.Defaults.jobStoreClass,
    driverDelegateClass: Class[?]
)
object JobStoreConfig {
  object Defaults {
    val isClustered: Boolean    = true
    val jobStoreClass: Class[?] = classOf[org.quartz.impl.jdbcjobstore.JobStoreTX]
  }
}

final case class ThreadPoolConfig(
    threadCount: Int,
)

final case class DataSourceConfig(
    dataSourceName: String = DataSourceConfig.Defaults.dataSourceName,
    provider: String = DataSourceConfig.Defaults.providerName,
    driverClass: Class[?],
    jdbcUrl: String,
    username: String,
    password: String,
    maxConnections: Int,
)
object DataSourceConfig {
  object Defaults {
    val dataSourceName: String = "ds"
    val providerName: String   = POOLING_PROVIDER_HIKARICP
  }
}

final case class Quartz4sConfig(
    jobStore: JobStoreConfig,
    threadPool: ThreadPoolConfig,
    dataSource: DataSourceConfig,
) {
  private[scheduler] def toPropertiesMap: Map[String, String] = {
    val dataSourcePropPrefix = s"$PROP_DATASOURCE_PREFIX.${dataSource.dataSourceName}"
    Map(
      PROP_JOB_STORE_CLASS                          -> jobStore.jobStoreClass.getName,
      s"$PROP_JOB_STORE_PREFIX.driverDelegateClass" -> jobStore.driverDelegateClass.getName,
      s"$PROP_JOB_STORE_PREFIX.isClustered"         -> jobStore.isClustered.toString,
      s"$PROP_THREAD_POOL_PREFIX.threadCount"       -> threadPool.threadCount.toString,
      s"$PROP_JOB_STORE_PREFIX.dataSource"          -> dataSource.dataSourceName,
      s"$dataSourcePropPrefix.$POOLING_PROVIDER"    -> dataSource.provider,
      s"$dataSourcePropPrefix.$DB_DRIVER"           -> dataSource.driverClass.getName,
      s"$dataSourcePropPrefix.$DB_URL"              -> dataSource.jdbcUrl,
      s"$dataSourcePropPrefix.$DB_USER"             -> dataSource.username,
      s"$dataSourcePropPrefix.$DB_PASSWORD"         -> dataSource.password,
      s"$dataSourcePropPrefix.$DB_MAX_CONNECTIONS"  -> dataSource.maxConnections.toString,
    )
  }

  def toQuartzProperties: QuartzProperties = {
    val propMap = toPropertiesMap
    val props   = new Properties()
    propMap.foreach { case (k, v) => props.setProperty(k, v) }
    QuartzProperties(props)
  }
}
