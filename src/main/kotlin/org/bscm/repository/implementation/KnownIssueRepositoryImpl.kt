package org.bscm.repository.implementation

import org.bscm.models.KnownIssue
import org.bscm.repository.KnownIssueRepository
import java.util.*

class KnownIssueRepositoryImpl : KnownIssueRepository {
    override suspend fun addIssue(chartId: UUID, issue: KnownIssue): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun removeIssue(chartId: UUID, issueId: UUID): Boolean {
        TODO("Not yet implemented")
    }

}