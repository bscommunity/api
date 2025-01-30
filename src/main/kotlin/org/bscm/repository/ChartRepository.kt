package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.KnownIssue
import org.bscm.models.dto.CreateChartRequest
import org.bscm.models.dto.UpdateChartRequest
import java.time.LocalDateTime
import java.util.*

interface ChartRepository {
    suspend fun getAllCharts(startDate: LocalDateTime?, endDate: LocalDateTime?): List<Chart>
    suspend fun getChartById(id: UUID): Chart?
    suspend fun createChart(chart: CreateChartRequest): Chart
    suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart
    suspend fun deleteChart(id: UUID): Boolean
    //
    suspend fun addIssue(chartId: UUID, issue: KnownIssue): Boolean
    suspend fun removeIssue(chartId: UUID, issueId: UUID): Boolean
    //
    suspend fun addContributor(chartId: UUID, userId: UUID): Boolean
    suspend fun removeContributor(chartId: UUID, userId: UUID): Boolean
    suspend fun getContributors(chartId: UUID): List<UUID>
    //
    suspend fun addVersion(chartId: UUID): Boolean
    suspend fun removeVersion(chartId: UUID, versionId: UUID): Boolean
    suspend fun getVersions(chartId: UUID): List<UUID>
}
