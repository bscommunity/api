package org.bscm.repository

import org.bscm.models.Changelog
import org.bscm.models.interfaces.IChangelogRepository
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class ChangelogRepository : IChangelogRepository {
    override suspend fun addIssue(chartId: String, issue: Changelog): Changelog = newSuspendedTransaction {
        // TODO Bloco 9: implement changelog persistence
        issue
    }

    override suspend fun removeIssue(chartId: String, issueId: UUID): Boolean = newSuspendedTransaction {
        // TODO Bloco 9: implement changelog persistence
        true
    }
}
