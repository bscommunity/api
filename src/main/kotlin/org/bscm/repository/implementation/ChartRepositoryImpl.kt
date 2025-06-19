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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
        val query = ChartTable.selectAll()
            .where { ChartTable.id eq id }

        applyJoinsAndSelect(query, fetchVersions = -1, fetchContributors = true, fetchStreamingLinks = false)
        val processedResults = processResultsInMemory(
            query.toList(),
            includeVersions = true,
            includeContributors = true,
            includeStreamingLinks = false
        )

        if (processedResults.isEmpty()) return@newSuspendedTransaction null

        val chartResult = processedResults.first()

        daoToChart(
            entity = chartResult.chart,
            versions = chartResult.versions?.map { versionEntityToVersion(it) },
            contributors = chartResult.contributors?.map {
                contributorEntityToContributor(it.component1(), it.component2())
            }
        )
    }

    override suspend fun getAppChartById(id: UUID): AppChart? = newSuspendedTransaction {
        val query = ChartTable.selectAll()
            .where { ChartTable.id eq id }

        applyJoinsAndSelect(query, fetchVersions = 1, fetchContributors = true, fetchStreamingLinks = true)
        val processedResults = processResultsInMemory(
            query.toList(),
            includeVersions = true,
            includeContributors = true,
            includeStreamingLinks = true
        )

        if (processedResults.isEmpty()) return@newSuspendedTransaction null

        val chartResult = processedResults.first()
        daoToAppChart(
            entity = chartResult.chart,
            streamingLinks = chartResult.streamingLinks?.map { daoToStreamingLink(it) },
            versions = chartResult.versions?.map { versionEntityToVersion(it) },
            contributors = chartResult.contributors?.map {
                contributorEntityToContributor(it.component1(), it.component2())
            }
        )
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
        fetchVersions: Int = 0,
        fetchContributors: Boolean = true,
        fetchStreamingLinks: Boolean = true
    ): List<ChartResult> {
        val startTime = System.currentTimeMillis()

        // First, fetch the correctly filtered and sorted IDs with pagination
        val baseQuery = ChartTable.select(ChartTable.id)
        applyAllFilters(baseQuery, userId, chartIds, search, difficulties, genres)
        applyOrdering(baseQuery, sortBy)

        limit?.takeIf { it > 0 }?.let { baseQuery.limit(it) }
        offset?.takeIf { it >= 0 }?.let { baseQuery.offset(it.toLong()) }

        val paginatedIds = baseQuery.map { it[ChartTable.id].value }
        if (paginatedIds.isEmpty()) return emptyList()

        // Now, fetch the full data for those specific IDs
        val fullQuery = ChartTable.selectAll().where { ChartTable.id inList paginatedIds }

        // Decoupled join logic
        applyJoinsAndSelect(fullQuery, fetchVersions, fetchContributors, fetchStreamingLinks)

        // Execute the query to get all chart data
        val results = fullQuery.toList()


        // Process results to group related entities (versions, contributors, etc.)
        val processedResults = processResultsInMemory(
            results,
            includeVersions = fetchVersions != 0,
            includeContributors = fetchContributors,
            includeStreamingLinks = fetchStreamingLinks
        )

        println("Fetched ${processedResults.size} with filters: userId=$userId, chartIds=${chartIds?.joinToString()}, search=$search, sortBy=$sortBy, difficulties=${difficulties?.joinToString()}, genres=${genres?.joinToString()}, limit=$limit, offset=$offset")

        // The database does not guarantee order with an `IN` clause,
        // so we re-sort the results in memory based on the correctly ordered `paginatedIds`.
        val chartMap = processedResults.associateBy { it.chart.id.value }
        val sortedResults = paginatedIds.mapNotNull { id -> chartMap[id] }

        val endTime = System.currentTimeMillis()
        println("Charts fetch completed in ${endTime - startTime}ms with ${sortedResults.size} charts")

        return sortedResults
    }

    /**
     * Applies all optional joins to the main query and adjusts the selected columns.
     */
    private fun applyJoinsAndSelect(
        query: Query,
        fetchVersions: Int = 0,
        fetchContributors: Boolean = false,
        fetchStreamingLinks: Boolean = false,
    ) {
        val columnsToSelect = mutableListOf<Column<*>>(*ChartTable.columns.toTypedArray())

        if (fetchContributors) {
            query.adjustColumnSet {
                leftJoin(ContributorTable, { ChartTable.id }, { ContributorTable.chartId })
                    .leftJoin(UserTable, { ContributorTable.userId }, { UserTable.id })
            }
            columnsToSelect.addAll(ContributorTable.columns)
            columnsToSelect.addAll(UserTable.columns)
        }

        if (fetchStreamingLinks) {
            query.adjustColumnSet {
                leftJoin(ChartStreamingLinkTable, { ChartTable.id }, { ChartStreamingLinkTable.chartId })
                    .leftJoin(
                        StreamingLinkTable,
                        { ChartStreamingLinkTable.streamingLinkId },
                        { StreamingLinkTable.id })
            }
            columnsToSelect.addAll(StreamingLinkTable.columns)
        }

        if (fetchVersions < 0) {
            query.adjustColumnSet {
                leftJoin(VersionTable, { ChartTable.id }, { VersionTable.chartId })
            }
            columnsToSelect.addAll(VersionTable.columns)
        } else if (fetchVersions == 1) {
            query.adjustColumnSet {
                innerJoin(
                    VersionTable,
                    { ChartTable.latestVersionId },
                    { VersionTable.id }
                )
            }
            columnsToSelect.addAll(VersionTable.columns)
        }

        query.adjustSelect { select(columnsToSelect) }
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

        // Filter by a specific list of chartIds if provided
        chartIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            query.andWhere { ChartTable.id inList ids }
        }

        // Search functionality for artist, track, or album
        if (!search.isNullOrBlank()) {
            applySearch(search, query)
        }

        // Filter by difficulties
        difficulties?.takeIf { it.isNotEmpty() }?.let {
            query.andWhere { ChartTable.difficulty inList it }
        }

        // Filter by genres
        genres?.takeIf { it.isNotEmpty() }?.let {
            query.andWhere { ChartTable.genre inList it }
        }
    }

    private fun applySearch(
        search: String,
        query: Query
    ) {
        val normalizedSearchTerm = QueryUtils.getNormalizedQuery(search)
        val searchTerms = normalizedSearchTerm.split(" ").filter { it.isNotBlank() } // Split and filter empty terms

        // --- WHERE Clause: Combine search conditions ---
        val whereConditions = mutableListOf<Op<Boolean>>()

        // 1. Exact phrase match on any normalized field (highest relevance)
        val exactPhraseMatchCondition = (ChartTable.normalizedArtist like "%$normalizedSearchTerm%") or
                (ChartTable.normalizedTrack like "%$normalizedSearchTerm%") or
                (ChartTable.normalizedAlbum like "%$normalizedSearchTerm%")
        whereConditions.add(exactPhraseMatchCondition)

        // 2. All individual terms present in any normalized field (good relevance)
        if (searchTerms.isNotEmpty()) {
            val allTermsPresentCondition = searchTerms.map { term ->
                (ChartTable.normalizedArtist like "%$term%") or
                        (ChartTable.normalizedTrack like "%$term%") or
                        (ChartTable.normalizedAlbum like "%$term%")
            }.reduce { acc, cond -> acc and cond } // All individual terms must be present somewhere
            whereConditions.add(allTermsPresentCondition)
        }

        // Combine all WHERE conditions with OR
        if (whereConditions.isNotEmpty()) {
            query.andWhere {
                whereConditions.reduce { acc, cond -> acc or cond }
            }
        } else {
            // Should not happen if search is not blank, but as a safeguard
            return // No conditions
        }
    }

    private fun applyOrdering(query: Query, sortBy: ChartSortOption) {
        when (sortBy) {
            ChartSortOption.LAST_UPDATED -> {
                query
                    .adjustColumnSet {
                        innerJoin(VersionTable, { ChartTable.latestVersionId }, { VersionTable.id })
                    }
                    .orderBy(VersionTable.publishedAt to SortOrder.DESC)
            }

            else -> { // Defaults to MOST_DOWNLOADED
                query
                    .adjustColumnSet {
                        // This join is necessary to access the downloadsAmount for summation
                        leftJoin(VersionTable, { ChartTable.id }, { VersionTable.chartId })
                    }
                    .groupBy(ChartTable.id)
                    .orderBy(VersionTable.downloadsAmount.sum() to SortOrder.DESC)
            }
        }
    }

    private fun processResultsInMemory(
        results: List<ResultRow>,
        includeVersions: Boolean,
        includeContributors: Boolean,
        includeStreamingLinks: Boolean
    ): List<ChartResult> {
        // Group the rows by Chart ID
        val groupedByChartId = results.groupBy { it[ChartTable.id].value }

        return groupedByChartId.map { (chartId, rows) ->
            val chartEntity = ChartEntity.wrapRow(rows.first())

            val streamingLinks = if (includeStreamingLinks) {
                rows.mapNotNull { row ->
                    // Check if streaming link data exists in this row
                    row.getOrNull(StreamingLinkTable.id)?.let { streamingLinkId ->
                        // Also check if URL exists to ensure it's not a NULL join result
                        row.getOrNull(StreamingLinkTable.url)?.let {
                            StreamingLinkEntity.wrapRow(row)
                        }
                    }
                }.distinctBy { it.id.value } // Use .value for UUID comparison
            } else emptyList()

            val versions = if (includeVersions) {
                rows.mapNotNull { row ->
                    row.getOrNull(VersionTable.id)?.let { versionId ->
                        // Additional null check for version-specific data
                        row.getOrNull(VersionTable.index)?.let {
                            VersionEntity.wrapRow(row)
                        }
                    }
                }.distinctBy { it.id.value } // Use .value for UUID comparison
            } else null

            val contributors = if (includeContributors) {
                rows.mapNotNull { row ->
                    // Check if contributor data exists
                    row.getOrNull(ContributorTable.userId)?.let { userId ->
                        row.getOrNull(UserTable.id)?.let { userTableId ->
                            val contributor = ContributorEntity.wrapRow(row)
                            val user = UserEntity.wrapRow(row)
                            contributor to user
                        }
                    }
                }.distinctBy { it.first.id.value } // Use the composite ID value for distinction
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
            fetchVersions = if (fetchVersions) -1 else 0,
        )

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
            fetchVersions = 1
        )

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

        println("Suggestions returned in ${endTime - startTime}ms with ${result.size} results")
        result
    }

    override suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart = newSuspendedTransaction {
        // Create the chart
        val newChart = ChartEntity.new {
            this.artist = chart.artist
            this.track = chart.track
            this.album = chart.album
            this.trackPreviewUrl = chart.trackPreviewUrl
            this.coverUrl = chart.coverUrl
            this.difficulty = chart.difficulty
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
            this.genre = chart.genre
            this.latestVersion = null
        }

        flushCache()

        // Handle streaming links with duplicate prevention
        val streamingLinkIds = chart.trackUrls.map { streamingLinkRequest ->
            // Try to find existing streaming link first
            val existingLink = StreamingLinkEntity.find {
                (StreamingLinkTable.platform eq streamingLinkRequest.platform) and
                        (StreamingLinkTable.url eq streamingLinkRequest.url)
            }.firstOrNull()

            if (existingLink != null) {
                // println("Using existing streaming link: ${existingLink.platform} - ${existingLink.url}")
                // Use existing streaming link
                existingLink.id.value
            } else {
                // Create new streaming link
                // println("Creating new streaming link: ${streamingLinkRequest.platform} - ${streamingLinkRequest.url}")
                val newLink = StreamingLinkEntity.new {
                    this.platform = streamingLinkRequest.platform
                    this.url = streamingLinkRequest.url
                }
                newLink.id.value
            }
        }

        // Link the chart to the streaming links through the junction table
        ChartStreamingLinkTable.batchInsert(streamingLinkIds) { streamingLinkId ->
            this[ChartStreamingLinkTable.chartId] = newChart.id
            this[ChartStreamingLinkTable.streamingLinkId] = streamingLinkId
        }

        // Add the initial version with the chart's metadata
        val initialVersion = VersionEntity.new {
            this.chart = newChart
            this.index = 0
            this.chartUrl = chart.chartUrl
            this.chartPreviewUrl = chart.chartPreviewUrl
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
        val contributor = ContributorEntity.new(contributorId) {
            roles = listOf(ContributorRole.AUTHOR)
            joinedAt = LocalDate.now()
        }

        // Since the new chart will be cached on the creator device,
        // we need to return all the data to avoid inconsistencies
        daoToChart(
            newChart,
            listOf(versionEntityToVersion(initialVersion)),
            listOf(contributorEntityToContributor(contributor))
        )
    }

    override suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart = newSuspendedTransaction {
        val existingChart = ChartEntity.findSingleByAndUpdate(ChartTable.id eq id) {
            it.artist = chart.artist ?: it.artist
            it.track = chart.track ?: it.track
            it.coverUrl = chart.coverUrl ?: it.coverUrl
            it.difficulty = chart.difficulty ?: it.difficulty
            it.isDeluxe = chart.isDeluxe ?: it.isDeluxe
            it.isExplicit = chart.isExplicit ?: it.isExplicit
            it.isFeatured = chart.isFeatured ?: it.isFeatured
            it.isPublic = chart.isPublic ?: it.isPublic
            it.genre = chart.genre ?: it.genre
        }

        if (existingChart == null) {
            throw NotFoundException("Chart with ID $id not found")
        }

        // TODO: It's not performant quite performant, but it works with the dashboard for now
        daoToChart(
            entity = existingChart,
            contributors = existingChart.contributors.map { contributorEntityToContributor(it) },
            versions = existingChart.versions.map { versionEntityToVersion(it) }
        )
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


