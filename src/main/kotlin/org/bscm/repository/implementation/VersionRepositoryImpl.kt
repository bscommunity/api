package org.bscm.repository.implementation

import org.bscm.models.Version
import org.bscm.models.entities.ChartEntity
import org.bscm.models.entities.VersionEntity
import org.bscm.models.tables.VersionTable
import org.bscm.repository.VersionRepository
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class VersionRepositoryImpl : VersionRepository {
    companion object {
        fun versionEntityToVersion(entity: VersionEntity): Version = Version(
            id = entity.id.value,
            chartId = entity.chart.id.value,
            chartUrl = entity.chartUrl,
            duration = entity.duration,
            notesAmount = entity.notesAmount,
            effectsAmount = entity.effectsAmount,
            bpm = entity.bpm,
            downloadsAmount = entity.downloadsAmount,
            knownIssues = entity.knownIssues,
            publishedAt = entity.publishedAt,
        )
    }

    override suspend fun addVersion(version: Version): Version = newSuspendedTransaction {
        val chartEntity = ChartEntity.findById(version.chartId) ?: throw IllegalArgumentException("Chart not found")

        val versionEntity = VersionEntity.new {
            this.chart = chartEntity
            this.chartUrl = version.chartUrl
            this.duration = version.duration
            this.notesAmount = version.notesAmount
            this.effectsAmount = version.effectsAmount
            this.bpm = version.bpm
            this.downloadsAmount = version.downloadsAmount
            this.knownIssues = version.knownIssues
            this.publishedAt = version.publishedAt
        }

        // Update the latest version of the chart
        ChartEntity.findByIdAndUpdate(chartEntity.id.value) {
            it.latestVersion = versionEntity
        }

        versionEntityToVersion(versionEntity)
    }

    override suspend fun removeVersion(versionId: Int): Boolean = newSuspendedTransaction {
        val versionEntity = VersionEntity.findById(versionId)
            ?: throw IllegalArgumentException("Version not found")

        versionEntity.delete()
        true
    }

    override suspend fun getVersions(chartId: UUID): List<Version> = newSuspendedTransaction {
        VersionEntity.find { VersionTable.chartId eq chartId }.map { versionEntityToVersion(it) }
    }

    override suspend fun getLatestVersionsByChartIds(chartIds: List<UUID>): List<Version> = newSuspendedTransaction {
        chartIds.mapNotNull { chartId ->
            VersionEntity
                .find { VersionTable.chartId eq chartId }
                .orderBy(VersionTable.publishedAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.let { versionEntityToVersion(it) }
        }
    }
}