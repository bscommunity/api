package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.User
import org.bscm.models.dto.CreateUserRequest
import org.bscm.models.dto.UpdateUserRequest
import java.util.UUID

interface ChartRepository {
    suspend fun getAllCharts(): List<Chart>
    suspend fun getChartById(id: UUID): Chart?
    suspend fun createChart(chart: Chart): Chart
    suspend fun updateChart(id: UUID, chart: Chart): Chart
    suspend fun deleteChart(id: UUID): Boolean
}
