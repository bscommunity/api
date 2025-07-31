package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.KnownIssue
import org.bscm.models.dao.ChartEntity
import org.bscm.repository.KnownIssueRepository
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class KnownIssueRepositoryImpl : KnownIssueRepository {
    override suspend fun addIssue(chartId: ULong, issue: KnownIssue): KnownIssue = newSuspendedTransaction {
        val chart = ChartEntity.findByIdAndUpdate(chartId) {
            it.latestVersion?.knownIssues = it.latestVersion?.knownIssues.orEmpty() + issue
        } ?: throw NotFoundException("Chart with id $chartId not found")

        chart.latestVersion?.knownIssues?.last() ?: throw IllegalStateException("Failed to add issue")
    }

    override suspend fun removeIssue(chartId: ULong, issueId: UUID): Boolean = newSuspendedTransaction {
        ChartEntity.findByIdAndUpdate(chartId) {
            it.latestVersion?.knownIssues = it.latestVersion?.knownIssues.orEmpty().filter { it.id != issueId }
        } ?: throw NotFoundException("Chart with id $chartId not found")

        true
    }
}