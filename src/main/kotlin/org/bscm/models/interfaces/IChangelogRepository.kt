package org.bscm.models.interfaces

import org.bscm.models.Changelog
import java.util.*

interface IChangelogRepository {
    suspend fun addIssue(chartId: ULong, issue: Changelog): Changelog
    suspend fun removeIssue(chartId: ULong, issueId: UUID): Boolean
}