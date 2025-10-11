package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.AnalyticsOption
import org.bscm.models.enums.ChartSortOption
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import java.util.*

interface ChartRepository {
    suspend fun getCharts(userId: UUID?, contentIds: List<String>? = null, chartIds: List<ULong>? = null): List<Chart>
    suspend fun getFullCharts(
        userId: UUID?,
        search: String?,
        sortBy: ChartSortOption?,
        difficulties: List<Difficulty>? = null,
        genres: List<Genre>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<Chart>
    suspend fun getAppCharts(
        userId: UUID?,
        search: String?,
        sortBy: ChartSortOption?,
        difficulties: List<Difficulty>? = null,
        genres: List<Genre>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<Chart>
    suspend fun getSuggestions(query: String, limit: Int): List<String>
    suspend fun getChartById(id: ULong): Chart?
    suspend fun getAppChartById(contentId: String): Chart?
    suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart
    suspend fun updateChart(id: ULong, chart: UpdateChartRequest): Chart
    suspend fun deleteChart(id: ULong): Boolean
    suspend fun postAnalytics(chartId: ULong, action: AnalyticsOption): Boolean
    suspend fun refreshChartsBundles(ids: Map<String, String>): Boolean
}
