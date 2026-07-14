package org.bscm.repository

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
import java.time.LocalDateTime
import java.util.*

class ChangelogRepository : IChangelogRepository {
    override suspend fun addIssue(chartId: String, issue: Changelog): Changelog = suspendTransaction {
        val now = if (issue.createdAt == LocalDateTime.MIN) LocalDateTime.now() else issue.createdAt
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
