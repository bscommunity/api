package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import java.util.*

interface ChartRepository {
    suspend fun getCharts(
        chartIds: List<UUID>?,
        query: String?,
        limit: Int? = null,
        offset: Int? = null,
        fetchVersions: Boolean = false,
        fetchContributors: Boolean = false
    ): List<Chart>
    suspend fun getSuggestions(query: String, limit: Int): List<String>
    suspend fun getChartById(id: UUID): Chart?
    suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart
    suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart
    suspend fun deleteChart(id: UUID): Boolean
}
