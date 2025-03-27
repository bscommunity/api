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
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContributorTable
import org.bscm.models.tables.VersionTable
import org.bscm.repository.ChartRepository
import org.bscm.repository.implementation.ContributorRepositoryImpl.Companion.contributorEntityToContributor
import org.bscm.repository.implementation.VersionRepositoryImpl.Companion.versionEntityToVersion
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
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
        trackUrls = entity.trackUrls,
        trackPreviewUrl = entity.trackPreviewUrl,
        coverUrl = entity.coverUrl,
        isDeluxe = entity.isDeluxe,
        isExplicit = entity.isExplicit,
        difficulty = entity.difficulty,
        isFeatured = entity.isFeatured,
        latestVersion = entity.latestVersion?.let(::versionEntityToVersion),
        versions = versionEntities?.map(::versionEntityToVersion) ?: emptyList(),
        contributors = contributorEntities?.map(::contributorEntityToContributor) ?: emptyList(),
    )

    override suspend fun getCharts(
        chartIds: List<UUID>?,
        query: String?,
        limit: Int?,
        offset: Int?,
        fetchVersions: Boolean,
        fetchContributors: Boolean,
    ): List<Chart> = newSuspendedTransaction {
        val startTime = System.currentTimeMillis()

        // First, use Exposed's eager loading capabilities to load charts WITH latest versions in one query
        val chartQuery = when {
            !chartIds.isNullOrEmpty() -> ChartEntity.find { ChartTable.id inList chartIds }
            !query.isNullOrBlank() -> {
                val searchTerm = "%${query.lowercase()}%"
                ChartEntity.find {
                    (ChartTable.artist.lowerCase() like searchTerm) or
                            (ChartTable.track.lowerCase() like searchTerm) or
                            (ChartTable.album.lowerCase() like searchTerm)
                }
            }
            else -> ChartEntity.all()
        }

        // The key fix: Load charts with eager loading of latestVersion
        // This will generate a JOIN query instead of separate queries
        val paginatedCharts = chartQuery
            .orderBy(ChartTable.id to SortOrder.ASC)  // Consistent ordering
            .let {
                // Apply offset if specified (skip first N results)
                offset?.let { offset -> it.drop(offset) } ?: it
            }
            .let {
                // Apply limit if specified (take only N results)
                limit?.let { limit -> it.take(limit) } ?: it
            }
            .with(ChartEntity::latestVersion)  // Eager load latest versions
            .toList()

        // Early return for empty results
        if (paginatedCharts.isEmpty()) {
            val endTime = System.currentTimeMillis()
            println("Chart query completed in ${endTime - startTime}ms with 0 results")
            return@newSuspendedTransaction emptyList()
        }

        // Get all chart IDs for related data fetching
        val chartIdValues = paginatedCharts.map { it.id.value }

        // Batch load all versions if needed
        val versionsMap = if (fetchVersions) {
            VersionEntity.find { VersionTable.chartId inList chartIdValues }
                .toList()
                .groupBy { it.chart.id.value }
        } else {
            emptyMap()
        }

        // Batch load all contributors if needed
        val contributorsMap = if (fetchContributors) {
            ContributorEntity.find { ContributorTable.chartId inList chartIdValues }
                .toList()
                .groupBy { it.id.value[ContributorTable.chartId].value }
        } else {
            emptyMap()
        }

        // Map charts to domain models
        val result = paginatedCharts.map { entity ->
            chartEntityToChart(
                entity,
                versionEntities = versionsMap[entity.id.value],
                contributorEntities = contributorsMap[entity.id.value]
            )
        }

        val endTime = System.currentTimeMillis()
        println("Chart query completed in ${endTime - startTime}ms with ${result.size} results")

        result
    }

    override suspend fun getChartById(id: UUID): Chart? = newSuspendedTransaction {
        ChartEntity.findById(id)?.let { chartEntity ->
            chartEntityToChart(chartEntity, chartEntity.versions.toList(), chartEntity.contributors.toList())
        }
    }

    override suspend fun getSuggestions(query: String, limit: Int): List<String> = newSuspendedTransaction {
        val searchTerm = "%${query.lowercase()}%"
        ChartEntity.find {
            (ChartTable.artist.lowerCase() like searchTerm) or
                    (ChartTable.track.lowerCase() like searchTerm) or
                    (ChartTable.album.lowerCase() like searchTerm)
        }
            .limit(limit)
            // Deduplicate by artist, track, album, and default to track if no match is found
            .distinctBy { it ->
                listOf(it.artist, it.track, it.album).firstOrNull {
                    it?.lowercase()?.contains(query.lowercase()) ?: false
                } ?: it.track
            }
            // Map to the first matching field
            .map { it ->
                /*when {
                    it.artist.lowercase().contains(query.lowercase()) -> it.artist
                    it.track.lowercase().contains(query.lowercase()) -> it.track
                    it.album.lowercase().contains(query.lowercase()) -> it.album
                    else -> it.track // Default to track if no match is found
                }*/

                // Simplified version of the above code
                listOf(it.artist, it.track, it.album).firstOrNull {
                    it?.lowercase()?.contains(query.lowercase()) ?: false
                } ?: it.track
            }
    }

    override suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart = newSuspendedTransaction {
        // Create the chart
        val newChart = ChartEntity.new {
            this.artist = chart.artist
            this.track = chart.track
            this.album = chart.album
            this.trackUrls = chart.trackUrls
            this.trackPreviewUrl = chart.trackPreviewUrl
            this.coverUrl = chart.coverUrl
            this.difficulty = chart.difficulty
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
            this.latestVersion = null
        }

        flushCache()

        // Add the initial version with the chart's metadata
        val initialVersion = VersionEntity.new {
            this.chart = newChart
            this.index = 0
            this.chartUrl = chart.chartUrl
            this.chartPreviewUrls = chart.chartPreviewUrls
            this.duration = chart.duration
            this.notesAmount = chart.notesAmount
            this.effectsAmount = chart.effectsAmount
            this.bpm = chart.bpm
        }

        ChartEntity.findByIdAndUpdate(newChart.id.value) {
            it.latestVersion = initialVersion
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

        // Since the new chart will be cached on the creator device,
        // we need to return all the data to avoid inconsistencies
        chartEntityToChart(newChart, newChart.versions.toList(), newChart.contributors.toList())
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