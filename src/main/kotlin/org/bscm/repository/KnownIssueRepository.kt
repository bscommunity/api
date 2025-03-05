package org.bscm.repository

import org.bscm.models.KnownIssue
import java.util.*

interface KnownIssueRepository {
    suspend fun addIssue(chartId: UUID, issue: KnownIssue): Boolean
    suspend fun removeIssue(chartId: UUID, issueId: UUID): Boolean
}