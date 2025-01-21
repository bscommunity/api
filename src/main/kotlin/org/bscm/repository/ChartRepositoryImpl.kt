package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.Chart
import org.bscm.models.dto.CreateChartRequest
import org.bscm.models.dto.UpdateChartRequest
import org.bscm.models.entities.ChartEntity
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class ChartRepositoryImpl : ChartRepository {

    private fun chartEntityToChart(entity: ChartEntity): Chart = Chart(
        id = entity.id.value,
        name = entity.name,
        artist = entity.artist,
        coverUrl = entity.coverUrl,
        duration = entity.duration,
        isDeluxe = entity.isDeluxe,
        isExplicit = entity.isExplicit,
        isFeatured = entity.isFeatured,
        notesAmount = entity.notesAmount
    )

    override suspend fun getAllCharts(): List<Chart> = newSuspendedTransaction {
        ChartEntity.all().map(::chartEntityToChart)
    }

    override suspend fun getChartById(id: UUID): Chart? = newSuspendedTransaction {
        ChartEntity.findById(id)?.let(::chartEntityToChart)
    }

    override suspend fun createChart(chart: CreateChartRequest): Chart = newSuspendedTransaction {
        val newChart = ChartEntity.new(UUID.randomUUID()) {
            this.artist = chart.artist
            this.name = chart.name
            this.coverUrl = chart.coverUrl
            this.duration = chart.duration
            this.notesAmount = chart.notesAmount
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
            this.isFeatured = chart.isFeatured

        }
        chartEntityToChart(newChart)
    }

    override suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart = newSuspendedTransaction {
        val existingChart = ChartEntity.findById(id) ?: throw NotFoundException("Chart not found")
        existingChart.apply {
            artist = chart.artist ?: artist
            name = chart.name ?: name
            coverUrl = chart.coverUrl ?: coverUrl
            duration = chart.duration ?: duration
            notesAmount = chart.notesAmount ?: notesAmount
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