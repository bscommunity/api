package org.bscm.models.interfaces

import org.bscm.models.Chart
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.OperationOption
import org.bscm.models.enums.SortOption
import org.bscm.repository.ChartRepository.ChartAddons
import org.bscm.repository.ChartRepository.ChartFilters
import java.util.*

interface IChartRepository {
    suspend fun getCharts(
        sortBy: SortOption? = null,
        filters: ChartFilters? = null,
        addons: ChartAddons? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): Pair<List<Chart>, Int?>

    suspend fun getChartsByContentIds(contentIds: List<String>, addons: ChartAddons? = null): List<Chart>

    suspend fun getSuggestions(query: String, limit: Int): List<String>
    suspend fun getChartById(id: ULong, addons: ChartAddons? = null): Chart?
    suspend fun getChartByContentId(contentId: String, addons: ChartAddons? = null): Chart?
    suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart
    suspend fun updateChart(id: ULong, chart: UpdateChartRequest): Chart
    suspend fun deleteChart(id: ULong): Boolean
    suspend fun postAnalytics(chartId: ULong, action: OperationOption): Boolean
    suspend fun refreshChartsBundles(messages: Map<String, org.bscm.services.UploadService.RefreshData>): Boolean
}
