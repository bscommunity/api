package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import java.util.*

interface ChartRepository {
    suspend fun getCharts(fetchContributors: Boolean): List<Chart>
    suspend fun getChartById(id: UUID): Chart?
    suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart
    suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart
    suspend fun deleteChart(id: UUID): Boolean
}
