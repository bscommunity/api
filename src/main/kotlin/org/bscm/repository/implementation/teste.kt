package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.AppChart
import org.bscm.models.Chart
import org.bscm.models.Contributor
import org.bscm.models.Version
import org.bscm.models.dao.ChartEntity
import org.bscm.models.dao.ContributorEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dao.VersionEntity
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.*
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContributorTable
import org.bscm.repository.ChartRepository
import org.bscm.repository.implementation.ContributorRepositoryImpl.Companion.contributorEntityToContributor
import org.bscm.repository.implementation.VersionRepositoryImpl.Companion.versionEntityToVersion
import org.bscm.services.QueryUtils
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.util.*
import kotlin.math.min

class teste : ChartRepository {

    private fun daoToChart(
        entity: ChartEntity,
        versionEntities: List<Version>? = null,
        contributorEntities: List<Contributor>? = null
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
        isPublic = entity.isPublic,
        genre = entity.genre,
        versions = versionEntities ?: entity.versions.map { versionEntityToVersion(it) },
        contributors = contributorEntities ?: entity.contributors.map { contributorEntityToContributor(it) },
    )

    private fun daoToAppChart(
        entity: ChartEntity,
        contributorEntities: List<Contributor>? = null,
    ): AppChart = AppChart(
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
        genre = entity.genre,
        latestVersion = entity.latestVersion?.let(::versionEntityToVersion),
        downloadsSum = entity.versions.sumOf { it.downloadsAmount }, // Android app expects this field
        latestPublishedAt = entity.latestVersion?.publishedAt, // Android app expects this field
        contributors = contributorEntities ?: entity.contributors.map(::contributorEntityToContributor),
    )

    override suspend fun getChartById(id: UUID): Chart? = newSuspendedTransaction {
        ChartEntity.findById(id)?.let { chartEntity ->
            daoToChart(chartEntity)
        }
    }

    private fun fetchChartEntities(
        userId: UUID?,
        chartIds: List<UUID>?,
        query: String?,
        sortBy: ChartSortOption,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
        fetchVersions: Boolean,
        fetchContributors: Boolean = true,
    ): List<ChartEntity> {
        val startTime = System.currentTimeMillis()

        // Build the initial condition as always true
        var conditions: Op<Boolean> = Op.TRUE

        // Add chartIds filter if provided
        if (!chartIds.isNullOrEmpty()) {
            conditions = conditions and (ChartTable.id inList chartIds)
        }

        // Filter to only query public charts if userId is null
        if (userId == null) {
            conditions = conditions and ChartTable.isPublic eq Op.TRUE
        }

        // Add search query filter if provided
        if (!query.isNullOrBlank()) {
            val combinedMatches = QueryUtils.getSearchMatches(query, 50)

            // Create a condition that matches any of the search terms against artist, track, or album
            val chartMatchCondition = combinedMatches.map { match ->
                (ChartTable.artist.lowerCase() like "%${match.lowercase()}%") or
                        (ChartTable.track.lowerCase() like "%${match.lowercase()}%") or
                        (ChartTable.album.lowerCase() like "%${match.lowercase()}%")
            }.reduceOrNull { acc, cond -> acc or cond }

            if (chartMatchCondition != null) {
                conditions = conditions and chartMatchCondition
            } else {
                return emptyList() // No matches found, return empty list
            }
        }

        // Add difficulty filter if specified
        if (!difficulties.isNullOrEmpty()) {
            conditions = conditions and (ChartTable.difficulty inList difficulties)
        }

        // Add genres filter if specified
        if (!genres.isNullOrEmpty()) {
            conditions = conditions and (ChartTable.genre inList genres)
        }

        // Execute the query with all conditions applied
        val chartQuery = ChartEntity.find { conditions }.with(ChartEntity::versions) // Eager load versions

        // Apply sorting based on the effective sort option
        val sortedQuery = when (sortBy) {
            ChartSortOption.LAST_UPDATED -> chartQuery.sortedByDescending { it.latestVersion?.publishedAt }
            ChartSortOption.MOST_DOWNLOADED -> chartQuery.sortedByDescending { it.versions.sumOf { version -> version.downloadsAmount } }
            else -> chartQuery
        }

        // Apply pagination and eager load latest version
        val paginatedCharts = sortedQuery
            .drop(offset ?: 0)
            .take(limit ?: Int.MAX_VALUE)

        // Early return for empty results
        if (paginatedCharts.isEmpty()) {
            return emptyList()
        }

        val endTime = System.currentTimeMillis()

        println("Fetched ${paginatedCharts.size} charts in ${endTime - startTime}ms with conditions: $conditions, sortBy: $sortBy, limit: $limit, offset: $offset")
        println("Paginated charts: ${paginatedCharts.map { it.track }}")

        return paginatedCharts
    }

    override suspend fun getCharts(
        userId: UUID?,
        chartIds: List<UUID>?,
        query: String?,
        sortBy: ChartSortOption,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
        fetchVersions: Boolean,
    ): List<Chart> = newSuspendedTransaction {
        val paginatedCharts = fetchChartEntities(
            userId = userId,
            chartIds = chartIds,
            query = query,
            sortBy = sortBy,
            difficulties = difficulties,
            genres = genres,
            limit = limit,
            offset = offset,
            fetchVersions = fetchVersions
        )

        return@newSuspendedTransaction emptyList()

        /*val transformStart = System.currentTimeMillis()
        val result = paginatedCharts.map { daoToChart(it) }
        val transformEnd = System.currentTimeMillis()

        println("Transformation took ${transformEnd - transformStart}ms")
        return@newSuspendedTransaction result*/
    }

    override suspend fun getAppCharts(
        chartIds: List<UUID>?,
        query: String?,
        sortBy: ChartSortOption,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
    ): List<AppChart> = newSuspendedTransaction {
        emptyList()
    }

    override suspend fun getSuggestions(query: String, limit: Int): List<String> = newSuspendedTransaction {
        if (query.isBlank()) return@newSuspendedTransaction emptyList()

        val startTime = System.currentTimeMillis()
        val result = QueryUtils.getSearchMatches(query, min(limit, 10))
        val endTime = System.currentTimeMillis()

        println("Chart query completed in ${endTime - startTime}ms with ${result.size} results")
        result
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
            this.genre = chart.genre
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
            roles = listOf(ContributorRole.AUTHOR)
            joinedAt = LocalDate.now()
        }

        // Since the new chart will be cached on the creator device,
        // we need to return all the data to avoid inconsistencies
        daoToChart(newChart)
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
            isPublic = chart.isPublic ?: isPublic
            genre = chart.genre ?: genre
        }
        daoToChart(existingChart)
    }

    override suspend fun deleteChart(id: UUID): Boolean = newSuspendedTransaction {
        val chart = ChartEntity.findById(id) ?: return@newSuspendedTransaction false
        chart.delete()
        true
    }

    override suspend fun postAnalytics(chartId: UUID, action: AnalyticsOption): Boolean = newSuspendedTransaction {
        when (action) {
            AnalyticsOption.INSTALL, AnalyticsOption.UPDATE -> {
                // Handle download analytics
                ChartEntity.findByIdAndUpdate(chartId) { entity ->
                    entity.latestVersion?.let { version ->
                        version.downloadsAmount += 1
                    }
                }
                println("Download analytics recorded for chart $chartId")
                true
            }

            AnalyticsOption.DELETE -> throw NotFoundException("Cannot post analytics for deleted charts")
            AnalyticsOption.APP_UPDATE -> throw NotFoundException("Cannot post analytics for app updates")
        }
    }
}

