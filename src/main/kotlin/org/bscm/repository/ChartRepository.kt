package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.dto.CreateChartRequest
import org.bscm.models.dto.UpdateChartRequest
import java.util.*

interface ChartRepository {
    suspend fun getAllCharts(): List<Chart>
    suspend fun getChartById(id: UUID): Chart?
    suspend fun createChart(chart: CreateChartRequest): Chart
    suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart
    suspend fun deleteChart(id: UUID): Boolean
}
