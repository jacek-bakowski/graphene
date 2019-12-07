package com.graphene.reader.store.key

import com.graphene.common.HierarchyMetricPaths
import com.graphene.common.utils.DateTimeUtils
import com.graphene.reader.store.ElasticsearchClient
import com.graphene.reader.store.key.optimizer.IndexBasedElasticsearchQueryOptimizer
import com.graphene.reader.utils.ElasticsearchTestUtils
import io.mockk.every
import io.mockk.mockk
import java.util.stream.Collectors
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class IndexBasedKeySearchHandlerTest {

  private val elasticsearchClient = mockk<ElasticsearchClient>()
  private val indexBasedKeySearchHandler = IndexBasedKeySearchHandler(elasticsearchClient, IndexBasedElasticsearchQueryOptimizer())

  @Test
  internal fun `should return hierarchy metric paths composited by leaf`() {
    // given
    val response = ElasticsearchClient.Response.of(
      ElasticsearchTestUtils.searchResponse(arrayOf(
        arrayOf(Pair("depth", 4), Pair("leaf", true), Pair("0", "servers"), Pair("1", "server1"), Pair("2", "cpu"), Pair("3", "usage")),
        arrayOf(Pair("depth", 4), Pair("leaf", true), Pair("0", "servers"), Pair("1", "server2"), Pair("2", "cpu"), Pair("3", "usage"))
      )))

    every { elasticsearchClient.query(any(), any(), any()) } answers { response }
    every { elasticsearchClient.searchScroll(any()) } answers { ElasticsearchTestUtils.emptyResponse() }
    every { elasticsearchClient.clearScroll(any()) } answers { Unit }

    // when
    val hierarchyMetricPaths = indexBasedKeySearchHandler
      .getHierarchyMetricPaths(
        "NONE",
        "servers.*.cpu.usage",
        DateTimeUtils.currentTimeSeconds(),
        DateTimeUtils.currentTimeSeconds()
      )

    // then
    assertEquals(2, hierarchyMetricPaths.size)

    assertEquals(
      listOf(
        "servers.server1.cpu.usage",
        "servers.server2.cpu.usage"
      ),
      extractGraphitePaths(hierarchyMetricPaths))
  }

  @Test
  internal fun `should return hierarchy metric paths composited by branch`() {
    // given
    val response = ElasticsearchClient.Response.of(
      ElasticsearchTestUtils.searchResponse(arrayOf(
        arrayOf(Pair("depth", 4), Pair("leaf", true), Pair("0", "servers"), Pair("1", "server1"), Pair("2", "cpu"), Pair("3", "usage"))
      )))

    every { elasticsearchClient.query(any(), any(), any()) } answers { response }
    every { elasticsearchClient.searchScroll(any()) } answers { ElasticsearchTestUtils.emptyResponse() }
    every { elasticsearchClient.clearScroll(any()) } answers { Unit }

    // when
    val hierarchyMetricPaths = indexBasedKeySearchHandler
      .getHierarchyMetricPaths(
        "NONE",
        "servers.server1.*",
        DateTimeUtils.currentTimeSeconds(),
        DateTimeUtils.currentTimeSeconds()
      ).toList()

    // then
    assertEquals(1, hierarchyMetricPaths.size)

    assertEquals("servers.server1.cpu", hierarchyMetricPaths[0].id)
    assertEquals("cpu", hierarchyMetricPaths[0].text)
    assertEquals(HierarchyMetricPaths.BRANCH, hierarchyMetricPaths[0].leaf)
  }

  private fun extractGraphitePaths(hierarchyMetricPaths: MutableCollection<HierarchyMetricPaths.HierarchyMetricPath>): List<String> {
    return hierarchyMetricPaths
      .stream()
      .map { it.id }
      .collect(Collectors.toList())
  }
}
