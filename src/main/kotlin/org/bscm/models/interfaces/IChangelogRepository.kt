package org.bscm.models.interfaces

import org.bscm.models.Changelog
import java.util.*

interface IChangelogRepository {
    suspend fun addIssue(chartId: String, description: String): UUID
    suspend fun removeIssue(chartId: String, issueId: UUID): Boolean
    suspend fun getByChartIds(chartIds: List<String>): Map<String, List<Changelog>>
}
