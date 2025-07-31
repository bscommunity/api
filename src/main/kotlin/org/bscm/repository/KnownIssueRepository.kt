package org.bscm.repository

import org.bscm.models.KnownIssue
import java.util.*

interface KnownIssueRepository {
    suspend fun addIssue(chartId: ULong, issue: KnownIssue): KnownIssue
    suspend fun removeIssue(chartId: ULong, issueId: UUID): Boolean
}