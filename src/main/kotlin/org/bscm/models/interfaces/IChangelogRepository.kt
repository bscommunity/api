package org.bscm.models.interfaces

import org.bscm.models.Changelog
import java.util.*

interface IChangelogRepository {
    suspend fun addIssue(chartId: String, issue: Changelog): Changelog
    suspend fun removeIssue(chartId: String, issueId: UUID): Boolean
}
