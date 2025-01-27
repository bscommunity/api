package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.Chart
import org.bscm.models.Version
import org.bscm.models.dto.CreateChartRequest
import org.bscm.models.dto.UpdateChartRequest
import org.bscm.models.entities.ChartEntity
import org.bscm.models.entities.VersionEntity
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class ChartRepositoryImpl : ChartRepository {

    private fun chartEntityToChart(entity: ChartEntity, versionEntities: List<VersionEntity>? = null): Chart = Chart(
        id = entity.id.value,
        track = entity.track,
        artist = entity.artist,
        album = entity.album,
        coverUrl = entity.coverUrl,
        isDeluxe = entity.isDeluxe,
        isExplicit = entity.isExplicit,
        difficulty = entity.difficulty,
        isFeatured = entity.isFeatured,
        versions = versionEntities?.map(::versionEntityToVersion) ?: emptyList()
    )

    private fun versionEntityToVersion(entity: VersionEntity): Version = Version(
        id = entity.id.value,
        chartId = entity.chart.id.value,
        index = entity.index,
        chartUrl = entity.chartUrl,
        duration = entity.duration,
        notesAmount = entity.notesAmount,
        effectsAmount = entity.effectsAmount,
        bpm = entity.bpm,
        downloadsAmount = entity.downloadsAmount,
        knownIssues = entity.knownIssues,
        publishedAt = entity.publishedAt,
    )

    override suspend fun getAllCharts(): List<Chart> = newSuspendedTransaction {
        ChartEntity.all().map { chartEntity ->
            // Get the latest version for this chart using maxBy on index
            val latestVersion = chartEntity.versions
                .toList()
                .maxByOrNull { it.index }

            chartEntityToChart(chartEntity, latestVersion?.let { listOf(it) } ?: emptyList())
        }
    }

    override suspend fun getChartById(id: UUID): Chart? = newSuspendedTransaction {
        ChartEntity.findById(id)?.let { chartEntity ->
            chartEntityToChart(chartEntity, chartEntity.versions.toList())
        }
    }

    override suspend fun createChart(chart: CreateChartRequest): Chart = newSuspendedTransaction {
        val newChart = ChartEntity.new(UUID.randomUUID()) {
            this.artist = chart.artist
            this.track = chart.track
            this.album = chart.album
            this.coverUrl = chart.coverUrl
            this.difficulty = chart.difficulty
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
        }

        val initialVersion = VersionEntity.new {
            this.chart = newChart
            this.chartUrl = chart.chartUrl
            this.duration = chart.duration
            this.notesAmount = chart.notesAmount
            this.effectsAmount = chart.effectsAmount
            this.bpm = chart.bpm
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
}