package org.bscm.repository

import org.bscm.models.AppChart
import org.bscm.models.Chart
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.AnalyticsOption
import org.bscm.models.enums.ChartSortOption
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import java.util.*

interface ChartRepository {
    suspend fun getCharts(
        userId: UUID?,
        chartIds: List<UUID>?,
        search: String?,
        sortBy: ChartSortOption = ChartSortOption.LAST_UPDATED,
        difficulties: List<Difficulty>? = null,
        genres: List<Genre>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<Chart>
    suspend fun getCharts(
        chartIds: List<UUID>?,
        search: String?,
        sortBy: ChartSortOption = ChartSortOption.LAST_UPDATED,
        difficulties: List<Difficulty>? = null,
        genres: List<Genre>? = null,
        limit: Int? = null,
        offset: Int? = null,
        fetchStreamingLinks: Boolean = true,
    ): List<AppChart>
    suspend fun getSuggestions(query: String, limit: Int): List<String>
    suspend fun getChartById(id: UUID): Chart?
    suspend fun getAppChartById(id: UUID): AppChart?
    suspend fun createChart(userId: UUID, chart: CreateChartRequest): AppChart
    suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart
    suspend fun deleteChart(id: UUID): Boolean
    suspend fun updateChartLinks(
        chartId: UUID,
        links: Map<String, String>
    ): Boolean
    suspend fun postAnalytics(
        chartId: UUID,
        action: AnalyticsOption
    ): Boolean
}
