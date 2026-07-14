package org.bscm.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.Changelog
import org.bscm.models.interfaces.IChangelogRepository
import org.bscm.models.tables.CatalogItemTable
import org.bscm.models.tables.ChangelogTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class ChangelogRepository : IChangelogRepository {
    override suspend fun addIssue(chartId: String, issue: Changelog): Changelog = suspendTransaction {
        val now = if (issue.createdAt == LocalDateTime(1, 1, 1, 0, 0)) Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) else issue.createdAt
        ChangelogTable.insert {
            it[id] = issue.id
            it[ChangelogTable.chartId] = EntityID(chartId, CatalogItemTable)
            it[ChangelogTable.title] = issue.title
            it[ChangelogTable.description] = issue.description
            it[ChangelogTable.createdAt] = now
        }
        issue.copy(createdAt = now)
    }

    override suspend fun removeIssue(chartId: String, issueId: UUID): Boolean = suspendTransaction {
        ChangelogTable.deleteWhere {
            (ChangelogTable.id eq issueId) and (ChangelogTable.chartId eq EntityID(chartId, CatalogItemTable))
        } > 0
    }
}
