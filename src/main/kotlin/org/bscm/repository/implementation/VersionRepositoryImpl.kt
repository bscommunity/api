package org.bscm.repository.implementation

import org.bscm.models.Version
import org.bscm.models.dao.ChartEntity
import org.bscm.models.dao.VersionEntity
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.tables.VersionTable
import org.bscm.repository.VersionRepository
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class VersionRepositoryImpl : VersionRepository {
    companion object {
        fun versionEntityToVersion(entity: VersionEntity): Version = Version(
            id = entity.id.value,
            index = entity.index,
            chartId = entity.chart.id.value,
            bundleUrl = entity.bundleUrl,
            previewUrl = entity.previewUrl,
            duration = entity.duration,
            notesAmount = entity.notesAmount,
            effectsAmount = entity.effectsAmount,
            bpm = entity.bpm,
            isDeluxe = entity.isDeluxe,
            isExplicit = entity.isExplicit,
            difficulty = entity.difficulty,
            downloadsAmount = entity.downloadsAmount,
            knownIssues = entity.knownIssues,
            publishedAt = entity.publishedAt,
        )
    }

    override suspend fun getVersionById(id: ULong): Version? = newSuspendedTransaction {
        val versionEntity = VersionEntity.findById(id) ?: return@newSuspendedTransaction null
        versionEntityToVersion(versionEntity)
    }

    override suspend fun addVersion(chartId: ULong, id: ULong, version: CreateVersionRequest): Version = newSuspendedTransaction {
        val chartEntity = ChartEntity.findById(chartId) ?: throw IllegalArgumentException("Chart not found")

        val versionEntity = VersionEntity.new(id) {
            this.chart = chartEntity
            this.index = chartEntity.latestVersion?.index?.plus(1) ?: 0 // Increment index if latest version exists
            this.bundleUrl = version.bundleUrl
            this.previewUrl = version.previewUrl
            this.duration = version.duration
            this.difficulty = version.difficulty
            this.notesAmount = version.notesAmount
            this.effectsAmount = version.effectsAmount
            this.bpm = version.bpm
            this.downloadsAmount = 0
            this.knownIssues = chartEntity.latestVersion?.knownIssues ?: emptyList()
        }

        // Update the latest version of the chart
        ChartEntity.findByIdAndUpdate(chartId) {
            it.latestVersion = versionEntity
        }

        versionEntityToVersion(versionEntity)
    }

    override suspend fun removeVersion(versionId: ULong): Boolean = newSuspendedTransaction {
        val versionEntity = VersionEntity.findById(versionId) ?: throw IllegalArgumentException("Version not found")

        if (versionEntity.chart.latestVersion?.id?.value == versionId) {
            throw IllegalArgumentException("Cannot remove the latest version")
        }

        // Remove the version
        versionEntity.delete()

        true
    }

    override suspend fun getVersions(chartId: ULong): List<Version> = newSuspendedTransaction {
        VersionEntity.find { VersionTable.chartId eq chartId }.map { versionEntityToVersion(it) }
    }

    override suspend fun getLatestVersionsByChartIds(chartIds: List<ULong>): List<Version> = newSuspendedTransaction {
        if (chartIds.isEmpty()) return@newSuspendedTransaction emptyList()

        val versions = VersionEntity.find {
            VersionTable.chartId inList chartIds
        }.orderBy(VersionTable.chartId to SortOrder.ASC, VersionTable.publishedAt to SortOrder.DESC)
            .with(VersionEntity::chart)
            .toList()
            .distinctBy { it.chart.id.value }

        versions.map { versionEntityToVersion(it) }
    }
}