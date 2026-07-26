package org.bscm.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.Changelog
import org.bscm.models.interfaces.IChangelogRepository
import org.bscm.models.tables.CatalogItemTable
import org.bscm.models.tables.ChangelogTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class ChangelogRepository : IChangelogRepository {
    override suspend fun addIssue(chartId: String, description: String): UUID = suspendTransaction {
        val issue = ChangelogTable.insertAndGetId {
            it[ChangelogTable.chartId] = EntityID(chartId, CatalogItemTable)
            it[ChangelogTable.description] = description
            it[ChangelogTable.createdAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }

        issue.value
    }

    override suspend fun removeIssue(chartId: String, issueId: UUID): Boolean = suspendTransaction {
        ChangelogTable.deleteWhere {
            (ChangelogTable.id eq issueId) and (ChangelogTable.chartId eq EntityID(chartId, CatalogItemTable))
        } > 0
    }

    override suspend fun getByChartIds(chartIds: List<String>): Map<String, List<Changelog>> = suspendTransaction {
        if (chartIds.isEmpty()) return@suspendTransaction emptyMap()

        ChangelogTable
            .selectAll()
            .where { ChangelogTable.chartId inList chartIds.map { EntityID(it, CatalogItemTable) } }
            .groupBy { it[ChangelogTable.chartId].value }
            .mapValues { (_, rows) ->
                rows.map { row ->
                    Changelog(
                        id = row[ChangelogTable.id].value,
                        chartId = row[ChangelogTable.chartId].value,
                        description = row[ChangelogTable.description],
                        createdAt = row[ChangelogTable.createdAt],
                    )
                }
            }
    }
}
