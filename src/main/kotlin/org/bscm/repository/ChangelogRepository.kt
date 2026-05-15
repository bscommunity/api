package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.dao.ChartEntity
import org.bscm.models.interfaces.IChangelogRepository
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class ChangelogRepository : IChangelogRepository {
    override suspend fun addIssue(chartId: ULong, issue: Changelog): Changelog = newSuspendedTransaction {
        val chart = ChartEntity.findByIdAndUpdate(chartId) {
            it.latestVersion?.changelog = it.latestVersion?.changelog.orEmpty() + issue
        } ?: throw NotFoundException("Chart with id $chartId not found")

        chart.latestVersion?.changelog?.last() ?: throw IllegalStateException("Failed to add issue")
    }

    override suspend fun removeIssue(chartId: ULong, issueId: UUID): Boolean = newSuspendedTransaction {
        ChartEntity.findByIdAndUpdate(chartId) {
            it.latestVersion?.changelog = it.latestVersion?.changelog.orEmpty().filter { it.id != issueId }
        } ?: throw NotFoundException("Chart with id $chartId not found")

        true
    }
}