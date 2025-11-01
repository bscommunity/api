package org.bscm.models.repository

import org.bscm.models.KnownIssue
import java.util.*

interface IKnownIssueRepository {
    suspend fun addIssue(chartId: ULong, issue: KnownIssue): KnownIssue
    suspend fun removeIssue(chartId: ULong, issueId: UUID): Boolean
}