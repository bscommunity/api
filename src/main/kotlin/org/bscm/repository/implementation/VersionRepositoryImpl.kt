package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.Chart
import org.bscm.models.Version
import org.bscm.models.dao.ChartEntity
import org.bscm.models.dao.VersionEntity
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.VersionTable
import org.bscm.repository.VersionRepository
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

class VersionRepositoryImpl : VersionRepository {
    companion object {
        fun versionEntityToVersion(entity: VersionEntity): Version = Version(
            id = entity.id.value.toString(),
            index = entity.index,
            chartId = entity.chart.id.value.toString(),
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

    override suspend fun addVersion(chartId: ULong, version: CreateVersionRequest): Version = newSuspendedTransaction {
        val chartEntity = ChartEntity.findById(chartId) ?: throw IllegalArgumentException("Chart not found")

        val versionEntity = VersionEntity.new(version.id) {
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

    override suspend fun addVersion(chart: Chart, version: CreateVersionRequest): Version = newSuspendedTransaction {
        val convertedChartId = chart.id.toULong()

        if (version.id == null) {
            throw IllegalArgumentException("Version ID must be provided")
        }

        val insertedId = VersionTable.insertAndGetId {
            it[id] = version.id
            it[chartId] = convertedChartId
            it[index] = chart.latestVersion?.index?.plus(1) ?: 0 // Increment index if latest version exists
            it[bundleUrl] = version.bundleUrl
            it[previewUrl] = version.previewUrl
            it[duration] = version.duration
            it[difficulty] = version.difficulty
            it[notesAmount] = version.notesAmount
            it[effectsAmount] = version.effectsAmount
            it[bpm] = version.bpm
            it[downloadsAmount] = 0 // Initial downloads amount is 0
            it[knownIssues] = chart.latestVersion?.knownIssues ?: emptyList()
        }

        val updatedRowCount = ChartTable.update({ ChartTable.id eq convertedChartId }) {
            it[latestVersionId] = insertedId
        }

        if (updatedRowCount == 0) {
            throw IllegalArgumentException("Failed to update chart with new version")
        }

        val insertedRow = VersionEntity.findById(insertedId) ?: throw IllegalArgumentException("Inserted version not found")

        versionEntityToVersion(insertedRow)
    }

    override suspend fun removeVersion(latestVersion: Version, versionId: ULong): Boolean = newSuspendedTransaction {
        val versionEntity = VersionEntity.findById(versionId) ?: throw NotFoundException("Version not found")

        // Ensure that the version being removed is not the only version of the chart
        if (latestVersion.index < 2) {
            throw IllegalArgumentException("You can't remove the only version of a chart")
        }

        // Ensure that the version being removed is the latest version
        if (latestVersion.id != versionId.toString()) {
            println("Removing version with ID: $versionId from chart with latest version ID: ${latestVersion.id}")
            throw IllegalArgumentException("You can only remove the latest version of a chart")
        }

        /*val latestVersionAfterRemoval = VersionEntity.find {
            VersionTable.chartId eq versionEntity.chartId
        }.orderBy(VersionTable.index to SortOrder.DESC).firstOrNull()

        // Update the chart to remove the latest version
        ChartEntity.findByIdAndUpdate(versionEntity.chartId.value) {
            it.latestVersion = latestVersionAfterRemoval
        }*/

        // Before removing the version, we need to ensure that the chart's latest version is updated
        val chartEntity = versionEntity.chart

        val newLatestVersion = VersionEntity.find {
            VersionTable.chartId eq chartEntity.id.value and (VersionTable.id neq versionId)
        }.orderBy(VersionTable.index to SortOrder.DESC).firstOrNull()

        if (newLatestVersion == null) {
            throw IllegalArgumentException("Cannot remove the last version of a chart")
        }

        // Update the chart's latest version
        chartEntity.latestVersion = newLatestVersion

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