package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.Chart
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.entities.ChartEntity
import org.bscm.models.entities.ContributorEntity
import org.bscm.models.entities.UserEntity
import org.bscm.models.entities.VersionEntity
import org.bscm.models.enums.ContributorRole
import org.bscm.models.tables.ContributorTable
import org.bscm.repository.ChartRepository
import org.bscm.repository.implementation.ContributorRepositoryImpl.Companion.contributorEntityToContributor
import org.bscm.repository.implementation.VersionRepositoryImpl.Companion.versionEntityToVersion
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.util.*

class ChartRepositoryImpl : ChartRepository {

    private fun chartEntityToChart(
        entity: ChartEntity,
        versionEntities: List<VersionEntity>? = null,
        contributorEntities: List<ContributorEntity>? = null
    ): Chart = Chart(
        id = entity.id.value,
        track = entity.track,
        artist = entity.artist,
        album = entity.album,
        coverUrl = entity.coverUrl,
        isDeluxe = entity.isDeluxe,
        isExplicit = entity.isExplicit,
        difficulty = entity.difficulty,
        isFeatured = entity.isFeatured,
        versions = versionEntities?.map(::versionEntityToVersion) ?: emptyList(),
        contributors = contributorEntities?.map(::contributorEntityToContributor) ?: emptyList(),
    )

    override suspend fun getCharts(
        fetchContributors: Boolean,
    ): List<Chart> = newSuspendedTransaction {
        ChartEntity.all()
            .map { chartEntity ->
                // Get the latest version for this chart (last added)
                val latestVersion = chartEntity.versions.toList().maxByOrNull { it.publishedAt }

                // Return the chart with the latest version
                // Only include contributors if requested
                if (fetchContributors) {
                    chartEntityToChart(chartEntity, listOfNotNull(latestVersion), chartEntity.contributors.toList())
                } else {
                    chartEntityToChart(chartEntity, listOfNotNull(latestVersion))
                }
            }
    }

    override suspend fun getChartById(id: UUID): Chart? = newSuspendedTransaction {
        ChartEntity.findById(id)?.let { chartEntity ->
            chartEntityToChart(chartEntity, chartEntity.versions.toList(), chartEntity.contributors.toList())
        }
    }

    override suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart = newSuspendedTransaction {
        // Create the chart
        val newChart = ChartEntity.new(UUID.randomUUID()) {
            this.artist = chart.artist
            this.track = chart.track
            this.album = chart.album
            this.coverUrl = chart.coverUrl
            this.difficulty = chart.difficulty
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
        }

        // Add the initial version with the chart's metadata
        val initialVersion = VersionEntity.new {
            this.chart = newChart
            this.chartUrl = chart.chartUrl
            this.duration = chart.duration
            this.notesAmount = chart.notesAmount
            this.effectsAmount = chart.effectsAmount
            this.bpm = chart.bpm
        }

        val user = UserEntity.findById(userId) ?: throw NotFoundException("User not found")

        val contributorId = CompositeID {
            it[ContributorTable.chartId] = newChart.id
            it[ContributorTable.userId] = user.id
        }

        // Add the user as an author of the chart
        ContributorEntity.new(contributorId) {
            roles = listOf(ContributorRole.Author)
            joinedAt = LocalDate.now()
        }

        chartEntityToChart(newChart, listOf(initialVersion))
    }

    override suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart = newSuspendedTransaction {
        val existingChart = ChartEntity.findById(id) ?: throw NotFoundException("Chart not found")
        existingChart.apply {
            artist = chart.artist ?: artist
            track = chart.track ?: track
            coverUrl = chart.coverUrl ?: coverUrl
            difficulty = chart.difficulty ?: difficulty
            isDeluxe = chart.isDeluxe ?: isDeluxe
            isExplicit = chart.isExplicit ?: isExplicit
            isFeatured = chart.isFeatured ?: isFeatured
        }
        chartEntityToChart(existingChart)
    }

    override suspend fun deleteChart(id: UUID): Boolean = newSuspendedTransaction {
        val chart = ChartEntity.findById(id) ?: return@newSuspendedTransaction false
        chart.delete()
        true
    }

    /*override suspend fun addIssue(chartId: UUID, issue: KnownIssue): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun removeIssue(chartId: UUID, issueId: UUID): Boolean {
        TODO("Not yet implemented")
    }*/
}