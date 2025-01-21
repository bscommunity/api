package org.bscm.repository

import org.bscm.models.Chart
import java.util.*

class ChartRepositoryImpl : ChartRepository {
    override suspend fun getAllCharts(): List<Chart> {
        return emptyList()
    }

    override suspend fun getChartById(id: UUID): Chart? {
        return null
    }

    override suspend fun createChart(chart: Chart): Chart {
        return chart
    }

    override suspend fun updateChart(id: UUID, chart: Chart): Chart {
        return chart
    }

    override suspend fun deleteChart(id: UUID): Boolean {
        return true
    }
}