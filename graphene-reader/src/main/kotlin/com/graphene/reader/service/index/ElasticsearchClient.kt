package com.graphene.reader.service.index

import net.iponweb.disthene.reader.config.IndexConfiguration
import net.iponweb.disthene.reader.exceptions.TooMuchDataExpectedException
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.FilteredQueryBuilder
import org.elasticsearch.index.query.RegexpQueryBuilder
import org.elasticsearch.search.SearchHits
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.annotation.PreDestroy

@Component
class ElasticsearchClient(
  private val client: TransportClient,
  private val indexConfiguration: IndexConfiguration
) {

  @Throws(TooMuchDataExpectedException::class)
  fun regexpQuery(regexpQuery: RegexpQueryBuilder): Response {
    var searchResponse = client
      .prepareSearch(indexConfiguration.index)
      .setScroll(TimeValue(indexConfiguration.timeout.toLong()))
      .setSize(indexConfiguration.scroll)
      .setTypes(indexConfiguration.type)
      .setQuery(regexpQuery)
      .execute()
      .actionGet()

    throwIfExceededMaxPaths(searchResponse)

    return Response.of(searchResponse)
  }

  fun filteredQuery(filteredQuery: FilteredQueryBuilder): Response {
    val response = client
      .prepareSearch(indexConfiguration.index)
      .setScroll(TimeValue(indexConfiguration.timeout.toLong()))
      .setSize(indexConfiguration.scroll)
      .setTypes(indexConfiguration.type)
      .setQuery(filteredQuery)
      .execute()
      .actionGet()

    throwIfExceededMaxPaths(response)

    return Response.of(response)
  }

  fun searchScroll(response: Response): Response {
    val searchResponse = client
      .prepareSearchScroll(response.scrollId)
      .setScroll(TimeValue.timeValueMinutes(indexConfiguration.timeout.toLong()))
      .execute()
      .actionGet()

    return Response.of(searchResponse)
  }

  private fun throwIfExceededMaxPaths(response: SearchResponse) {
    // if total hits exceeds maximum - abort right away returning empty array
    if (response.hits.totalHits() > indexConfiguration.maxPaths) {
      ElasticsearchIndexService.logger.debug("Total number map paths exceeds the limit: " + response.hits.totalHits())
      throw TooMuchDataExpectedException(
        "Total number map paths exceeds the limit: "
          + response.hits.totalHits()
          + " (the limit is "
          + indexConfiguration.maxPaths
          + ")")
    }
  }

  @PreDestroy
  fun shutdown() {
    logger.info("Shutting down elasticsearch client")
    client.close()
  }

  data class Response internal constructor(
    val scrollId: String,
    val hits: SearchHits,
    val size: Int
  ) {
    companion object {
      fun of(response: SearchResponse): Response {
        return Response(
          response.scrollId,
          response.hits,
          response.hits.hits.size
        )
      }
    }
  }

  companion object {
    internal val logger = LoggerFactory.getLogger(ElasticsearchClient::class.java)
  }

}
