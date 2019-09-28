package com.graphene.writer.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties

import javax.annotation.PostConstruct
import java.util.ArrayList

/**
 * @author Andrei Ivanov
 */
@ConfigurationProperties(prefix = "graphene.writer.index")
class IndexConfiguration {

  var name: String? = null
  var index: String? = null
  var type: String? = null
  var isCache: Boolean = false
  var expire: Long = 0
  var cluster: List<String> = ArrayList()
  var port: Int = 0
  var bulk: IndexBulkConfiguration? = null

  @PostConstruct
  fun init() {
    logger.info("Load Graphene index configuration : {}", toString())
  }

  override fun toString(): String {
    return "IndexConfiguration{" +
      "name='" + name + '\''.toString() +
      ", index='" + index + '\''.toString() +
      ", type='" + type + '\''.toString() +
      ", cache=" + isCache +
      ", expire=" + expire +
      ", cluster=" + cluster +
      ", port=" + port +
      ", bulk=" + bulk +
      '}'.toString()
  }

  companion object {

    private val logger = LoggerFactory.getLogger(IndexConfiguration::class.java)
  }
}
