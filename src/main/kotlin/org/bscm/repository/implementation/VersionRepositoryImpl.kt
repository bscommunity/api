package org.bscm.repository.implementation

import org.bscm.models.Version
import org.bscm.models.dao.ChartEntity
import org.bscm.models.dao.VersionEntity
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.tables.VersionTable
import org.bscm.repository.VersionRepository
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class VersionRepositoryImpl : VersionRepository {
    companion object {
        fun versionEntityToVersion(entity: VersionEntity): Version = Version(
            id = entity.id.value,
            chartId = entity.chart.id.value,
            index = entity.index,
            chartUrl = entity.chartUrl,
            chartPreviewUrls = entity.chartPreviewUrls,
            duration = entity.duration,
            notesAmount = entity.notesAmount,
            effectsAmount = entity.effectsAmount,
            bpm = entity.bpm,
            downloadsAmount = entity.downloadsAmount,
            knownIssues = entity.knownIssues,
            publishedAt = entity.publishedAt,
        )
    }

    override suspend fun addVersion(version: CreateVersionRequest): Version = newSuspendedTransaction {
        val chartEntity = ChartEntity.findById(version.chartId) ?: throw IllegalArgumentException("Chart not found")

        val versionEntity = VersionEntity.new {
            this.chart = chartEntity
            this.index = chartEntity.versions.count().toInt()
            this.chartUrl = version.chartUrl
            this.chartPreviewUrls = version.chartPreviewUrls ?: emptyList()
            this.duration = version.duration
            this.notesAmount = version.notesAmount
            this.effectsAmount = version.effectsAmount
            this.bpm = version.bpm
            this.downloadsAmount = 0
            this.knownIssues = chartEntity.latestVersion?.knownIssues ?: emptyList()
        }

        // Update the latest version of the chart
        ChartEntity.findByIdAndUpdate(version.chartId) {
            it.latestVersion = versionEntity
        }

        versionEntityToVersion(versionEntity)
    }

    private suspend fun deleteVersion(entity: VersionEntity, index: Int, chartId: UUID) = newSuspendedTransaction {
        // We delete the version first, so the unique constraint on the index column is not violated
        entity.delete()

        // Update the indexes of the versions (decrement by 1 all versions with index greater than the removed one)
        VersionEntity
            .find { (VersionTable.chartId eq chartId) and (VersionTable.index greater index) }
            .forEach { it.index-- }

        true
    }

    override suspend fun removeVersion(index: Int, chartId: UUID): Boolean = newSuspendedTransaction {
        val chartVersions = VersionEntity.find { VersionTable.chartId eq chartId }
        val version = chartVersions.last { it.index == index }

        if (version.index == chartVersions.count().toInt() - 1) {
            throw IllegalArgumentException("Cannot remove the latest version")
        }

        deleteVersion(version, index, chartId)

        true
    }

    override suspend fun removeVersion(versionId: UUID): Boolean = newSuspendedTransaction {
        val versionEntity = VersionEntity.findById(versionId) ?: throw IllegalArgumentException("Version not found")

        if (versionEntity.index == versionEntity.chart.versions.count().toInt() - 1) {
            throw IllegalArgumentException("Cannot remove the latest version")
        }

        deleteVersion(versionEntity, versionEntity.index, versionEntity.chart.id.value)

        true
    }

    override suspend fun getVersions(chartId: UUID): List<Version> = newSuspendedTransaction {
        VersionEntity.find { VersionTable.chartId eq chartId }.map { versionEntityToVersion(it) }
    }

    override suspend fun getLatestVersionsByChartIds(chartIds: List<UUID>): List<Version> = newSuspendedTransaction {
        if (chartIds.isEmpty()) return@newSuspendedTransaction emptyList()

        val versions = VersionEntity.find {
            VersionTable.chartId inList chartIds
        }.orderBy(VersionTable.chartId to SortOrder.ASC, VersionTable.index to SortOrder.DESC)
            .with(VersionEntity::chart)
            .toList()
            .distinctBy { it.chart.id.value }

        versions.map { versionEntityToVersion(it) }
    }
}