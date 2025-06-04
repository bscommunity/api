package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.*
import org.bscm.models.dao.*
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.*
import org.bscm.models.tables.*
import org.bscm.repository.ChartRepository
import org.bscm.repository.implementation.ContributorRepositoryImpl.Companion.contributorEntityToContributor
import org.bscm.repository.implementation.VersionRepositoryImpl.Companion.versionEntityToVersion
import org.bscm.services.QueryUtils
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.util.*
import kotlin.math.min

class ChartRepositoryImpl : ChartRepository {

    private class ChartResult(
        val chart: ChartEntity,
        val streamingLinks: List<StreamingLinkEntity>?,
        val versions: List<VersionEntity>?,
        val contributors: List<Pair<ContributorEntity, UserEntity>>?
    )

    private fun daoToStreamingLink(
        entity: StreamingLinkEntity
    ): StreamingLink = StreamingLink(
        id = entity.id.value,
        chartId = entity.chartId.value,
        platform = entity.platform,
        url = entity.url,
    )

    private fun daoToChart(
        entity: ChartEntity,
        versions: List<Version>? = null,
        contributors: List<Contributor>? = null
    ): Chart = Chart(
        id = entity.id.value,
        track = entity.track,
        artist = entity.artist,
        album = entity.album,
        trackPreviewUrl = entity.trackPreviewUrl,
        coverUrl = entity.coverUrl,
        isDeluxe = entity.isDeluxe,
        isExplicit = entity.isExplicit,
        difficulty = entity.difficulty,
        isFeatured = entity.isFeatured,
        isPublic = entity.isPublic,
        genre = entity.genre,
        versions = versions ?: emptyList(),
        contributors = contributors ?: emptyList(),
    )

    private fun daoToAppChart(
        entity: ChartEntity,
        streamingLinks: List<StreamingLink>?,
        versions: List<Version>? = null,
        contributors: List<Contributor>? = null,
    ): AppChart {
        val latestVersion = versions?.maxByOrNull { it.publishedAt }

        return AppChart(
            id = entity.id.value,
            track = entity.track,
            artist = entity.artist,
            album = entity.album,
            trackUrls = streamingLinks ?: emptyList(),
            trackPreviewUrl = entity.trackPreviewUrl,
            coverUrl = entity.coverUrl,
            isDeluxe = entity.isDeluxe,
            isExplicit = entity.isExplicit,
            difficulty = entity.difficulty,
            isFeatured = entity.isFeatured,
            genre = entity.genre,
            latestVersion = latestVersion,
            downloadsSum = versions?.sumOf { it.downloadsAmount } ?: 0, // Android app expects this field
            latestPublishedAt = latestVersion?.publishedAt, // Android app expects this field
            contributors = contributors ?: emptyList(),
        )
    }

    override suspend fun getChartById(id: UUID): Chart? = newSuspendedTransaction {
        ChartEntity.findById(id)?.let { chartEntity ->
            daoToChart(
                chartEntity,
                chartEntity.versions.map { versionEntity ->
                    versionEntityToVersion(versionEntity)
                },
                chartEntity.contributors.map { contributorEntity ->
                    contributorEntityToContributor(contributorEntity)
                }
            )
        }
    }

    private fun fetchChartEntities(
        userId: UUID?,
        chartIds: List<UUID>?,
        search: String?,
        sortBy: ChartSortOption,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
        fetchVersions: Boolean,
        fetchContributors: Boolean = true,
        fetchStreamingLinks: Boolean = true
    ): List<ChartResult>? {
        val startTime = System.currentTimeMillis()

        var query = ChartTable.selectAll()

        if (fetchContributors) {
            query = query.adjustColumnSet {
                leftJoin(ContributorTable, { ChartTable.id }, { ContributorTable.chartId })
                    .leftJoin(UserTable, { ContributorTable.userId }, { UserTable.id })
            }
        }

        if (fetchStreamingLinks) {
            query = query.adjustColumnSet {
                leftJoin(StreamingLinkTable, { ChartTable.id }, { StreamingLinkTable.chartId })
            }
        }

        applyAllFilters(query, userId, chartIds, search, difficulties, genres)
        applyOrdering(query, sortBy, fetchVersions)

        // Apply pagination
        limit?.let { query.limit(it) }
        offset?.let { query.offset(it.toLong()) }

        // Include subqueries for versions, contributors, and streaming links if requested
        if (fetchContributors || fetchVersions || fetchStreamingLinks) {
            query.adjustSelect {
                select(
                    ChartTable.columns +
                            (if (fetchVersions) VersionTable.columns else emptyList()) +
                            (if (fetchContributors) ContributorTable.columns + UserTable.columns else emptyList()) +
                            (if (fetchStreamingLinks) StreamingLinkTable.columns else emptyList())
                )
            }
        }

        // Execute the query and fetch results
        val results = query.toList()

        // println(results.first().fieldIndex.keys.forEach { println("Column: $it") })
        // println("Contributor 0: ${contributorEntityToContributor(ContributorEntity.wrapRow(results[0]))}")

        // Process results in memory to avoid N+1 issues
        val processedResults = processResultsInMemory(results, fetchVersions, fetchContributors, fetchStreamingLinks)

        val endTime = System.currentTimeMillis()
        println("Charts fetch completed in ${endTime - startTime}ms with ${processedResults.size} charts")

        return processedResults
    }

    private fun applyAllFilters(
        query: Query,
        userId: UUID?,
        chartIds: List<UUID>?,
        search: String?,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?
    ) {
        // Only return public charts if userId is null
        if (userId == null) {
            query.andWhere {
                ChartTable.isPublic eq true
            }
        }

        // Filter by userId if provided
        chartIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            query.andWhere { ChartTable.id inList ids }
        }

        // Search functionality
        if (!search.isNullOrBlank()) {
            val searchTerms = search.split(" ").map { it.trim().lowercase() }
            val conditions = searchTerms.map { term ->
                (ChartTable.artist.lowerCase() like "%$term%") or
                        (ChartTable.track.lowerCase() like "%$term%") or
                        (ChartTable.album.lowerCase() like "%$term%")
            }
            query.andWhere { conditions.reduce { acc, cond -> acc or cond } }
        }

        // Filter by difficulties and genres
        difficulties?.takeIf { it.isNotEmpty() }?.let {
            query.andWhere { ChartTable.difficulty inList it }
        }

        genres?.takeIf { it.isNotEmpty() }?.let {
            query.andWhere { ChartTable.genre inList it }
        }
    }

    private fun applyOrdering(query: Query, sortBy: ChartSortOption, fetchVersions: Boolean) {
        when (sortBy) {
            ChartSortOption.LAST_UPDATED -> {
                query
                    .adjustColumnSet {
                        // If fetchVersions is true, we do a left join to get all versions
                        if (fetchVersions)
                            leftJoin(
                                VersionTable,
                                { ChartTable.id },
                                { VersionTable.chartId }
                            )
                        // If fetchVersions is false, we do an inner join to get only the latest version
                        else innerJoin(
                            VersionTable,
                            { ChartTable.latestVersionId },
                            { VersionTable.id }
                        )
                    }
                    .orderBy(VersionTable.publishedAt to SortOrder.DESC)
            }

            else -> {
                query.adjustColumnSet {
                    leftJoin(VersionTable, { ChartTable.id }, { VersionTable.chartId })
                }
                    .groupBy(
                        ChartTable.id, VersionTable.id, ContributorTable.userId, ContributorTable.chartId,
                        UserTable.id, StreamingLinkTable.id
                    )
                    .orderBy(VersionTable.downloadsAmount.sum() to SortOrder.DESC)
            }
        }
    }

    private fun processResultsInMemory(
        results: List<ResultRow>,
        fetchVersions: Boolean,
        fetchContributors: Boolean,
        fetchStreamingLinks: Boolean
    ): List<ChartResult> {
        // Group the rows by Chart ID
        val groupedByChartId = results.groupBy { it[ChartTable.id].value }

        return groupedByChartId.map { (chartId, rows) ->
            val chartEntity = ChartEntity.wrapRow(rows.first())

            val streamingLinks = if (fetchStreamingLinks) {
                rows.mapNotNull { row ->
                    row.getOrNull(StreamingLinkTable.id)?.let {
                        StreamingLinkEntity.wrapRow(row)
                    }
                }.distinctBy { it.id }
            } else emptyList()

            val versions = if (fetchVersions) {
                rows.mapNotNull { row ->
                    row.getOrNull(VersionTable.id)?.let {
                        VersionEntity.wrapRow(row)
                    }
                }.distinctBy { it.id }
            } else null

            val contributors = if (fetchContributors) {
                rows.map { row ->
                    val contributor = ContributorEntity.wrapRow(row)
                    val user = UserEntity.wrapRow(row)

                    contributor to user
                }.distinctBy { it.first.id.value }
            } else null

            ChartResult(
                chart = chartEntity,
                streamingLinks = streamingLinks,
                versions = versions,
                contributors = contributors
            )
        }
    }

    override suspend fun getCharts(
        userId: UUID?,
        chartIds: List<UUID>?,
        search: String?,
        sortBy: ChartSortOption,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
        fetchVersions: Boolean,
    ): List<Chart> = newSuspendedTransaction {
        val result = fetchChartEntities(
            userId,
            chartIds,
            search,
            sortBy,
            difficulties,
            genres,
            limit,
            offset,
            fetchVersions
        )

        if (result == null) {
            return@newSuspendedTransaction emptyList()
        }

        val charts = result.map { chartResult ->
            daoToChart(
                entity = chartResult.chart,
                versions = chartResult.versions?.map { versionEntityToVersion(it) },
                contributors = chartResult.contributors?.map {
                    contributorEntityToContributor(
                        it.component1(),
                        it.component2()
                    )
                },
            )
        }

        charts
    }

    override suspend fun getCharts(
        chartIds: List<UUID>?,
        search: String?,
        sortBy: ChartSortOption,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
        fetchStreamingLinks: Boolean,
    ): List<AppChart> = newSuspendedTransaction {
        val result = fetchChartEntities(
            null,
            chartIds,
            search,
            sortBy,
            difficulties,
            genres,
            limit,
            offset,
            true
        )

        if (result == null) {
            return@newSuspendedTransaction emptyList()
        }

        val charts = result.map { chartResult ->
            daoToAppChart(
                entity = chartResult.chart,
                streamingLinks = chartResult.streamingLinks?.map { daoToStreamingLink(it) },
                versions = chartResult.versions?.map { versionEntityToVersion(it) },
                contributors = chartResult.contributors?.map {
                    contributorEntityToContributor(
                        it.component1(),
                        it.component2()
                    )
                },
            )
        }

        charts
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
            this.normalizedArtist = QueryUtils.getNormalizedQuery(chart.artist)
            this.normalizedTrack = QueryUtils.getNormalizedQuery(chart.track)
            this.normalizedAlbum = chart.album?.let { QueryUtils.getNormalizedQuery(it) }
            this.trackPreviewUrl = chart.trackPreviewUrl
            this.coverUrl = chart.coverUrl
            this.difficulty = chart.difficulty
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
            this.genre = chart.genre
            this.latestVersion = null
        }

        flushCache()

        // Add the track URLs
        chart.trackUrls.forEach { streamingLink ->
            StreamingLinkEntity.new {
                this.chart = newChart
                this.platform = streamingLink.platform
                this.url = streamingLink.url
            }
        }

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
            normalizedArtist = chart.artist?.let { QueryUtils.getNormalizedQuery(it) } ?: normalizedArtist
            normalizedTrack = chart.track?.let { QueryUtils.getNormalizedQuery(it) } ?: normalizedTrack
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


