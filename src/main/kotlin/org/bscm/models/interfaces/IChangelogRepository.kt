package org.bscm.models.interfaces

import java.util.*

interface IChangelogRepository {
    suspend fun addIssue(chartId: String, description: String): UUID
    suspend fun removeIssue(chartId: String, issueId: UUID): Boolean
}
