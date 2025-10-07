package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.Chart
import org.bscm.models.Contributor
import org.bscm.models.StreamingLink
import org.bscm.models.Version
import org.bscm.models.dao.*
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.*
import org.bscm.models.tables.*
import org.bscm.repository.ChartRepository
import org.bscm.repository.implementation.ContributorRepositoryImpl.Companion.contributorEntityToContributor
import org.bscm.repository.implementation.VersionRepositoryImpl.Companion.versionEntityToVersion
import org.bscm.utils.QueryUtils
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import kotlin.math.min

class ChartRepositoryImpl : ChartRepository {

    private class ChartResult(
        val chart: ChartEntity,
        val streamingLinks: List<StreamingLinkEntity>?,
        val versions: List<VersionEntity>,
        val contributors: List<Pair<ContributorEntity, UserEntity>>
    )

    private fun daoToStreamingLink(
        entity: StreamingLinkEntity
    ): StreamingLink = StreamingLink(
        platform = entity.platform,
        url = entity.url,
    )

    private fun daoToChart(
        entity: ChartEntity,
        streamingLinks: List<StreamingLink>?,
        versions: List<Version>,
        contributors: List<Contributor>? = null,
    ): Chart {
        val latestVersion = versions.maxBy { it.publishedAt }

        return Chart(
            id = entity.id.value.toString(),
            contentId = entity.content.id.value,
            track = entity.track,
            artist = entity.artist,
            album = entity.album,
            trackUrls = streamingLinks ?: emptyList(),
            trackPreviewUrl = entity.trackPreviewUrl,
            coverUrl = entity.coverUrl,
            isPublic = entity.isPublic,
            isFeatured = entity.isFeatured,
            genre = entity.genre,
            versions = versions,
            contributors = contributors ?: emptyList(),
            // Room Database fields (Android app expects these fields)
            downloadsSum = versions.sumOf { it.downloadsAmount },
            latestVersion = latestVersion,
            latestPublishedAt = latestVersion.publishedAt,
        )
    }

    override suspend fun getChartById(id: ULong): Chart? = newSuspendedTransaction {
        val query = ChartTable.selectAll()
            .where { ChartTable.id eq id }

        applyJoinsAndSelect(query, fetchAllVersions = true, fetchStreamingLinks = false)
        val processedResults = processResultsInMemory(
            query.toList(),
            includeStreamingLinks = false
        )

        if (processedResults.isEmpty()) return@newSuspendedTransaction null

        val chartResult = processedResults.first()

        daoToChart(
            entity = chartResult.chart,
            streamingLinks = null,
            versions = chartResult.versions.map { versionEntityToVersion(it) },
            contributors = chartResult.contributors.map {
                contributorEntityToContributor(it.component1(), it.component2())
            }
        )
    }

    override suspend fun getAppChartById(contentId: String): Chart? = newSuspendedTransaction {
        val query = ChartTable.selectAll()
            .where { ChartTable.contentId eq contentId }

        applyJoinsAndSelect(query, fetchAllVersions = false, fetchStreamingLinks = true)
        val processedResults = processResultsInMemory(
            query.toList(),
            includeStreamingLinks = true
        )

        if (processedResults.isEmpty()) return@newSuspendedTransaction null

        val chartResult = processedResults.first()

        daoToChart(
            entity = chartResult.chart,
            streamingLinks = chartResult.streamingLinks?.map { daoToStreamingLink(it) },
            versions = chartResult.versions.map { versionEntityToVersion(it) },
            contributors = chartResult.contributors.map {
                contributorEntityToContributor(it.component1(), it.component2())
            }
        )
    }

    private fun fetchChartEntities(
        userId: UUID?,
        chartIds: List<ULong>?,
        search: String?,
        sortBy: ChartSortOption?,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
        fetchAllVersions: Boolean,
        fetchStreamingLinks: Boolean = true
    ): List<ChartResult> {
        val startTime = System.currentTimeMillis()

        // First, fetch the correctly filtered and sorted IDs with pagination
        val baseQuery = ChartTable.select(ChartTable.id)
        applyAllFilters(baseQuery, userId, chartIds, search, difficulties, genres)
        applyOrdering(baseQuery, sortBy ?: ChartSortOption.LAST_UPDATED)

        // println("Base query: ${baseQuery.prepareSQL(QueryBuilder(false))}")

        limit?.takeIf { it > 0 }?.let { baseQuery.limit(it) }
        offset?.takeIf { it >= 0 }?.let { baseQuery.offset(it.toLong()) }

        val paginatedIds = baseQuery.map { it[ChartTable.id].value }
        if (paginatedIds.isEmpty()) {
            // println("No charts found with the provided filters.")
            return emptyList()
        }

        println("Paginated IDs: ${paginatedIds.joinToString()}")

        // Now, fetch the full data for those specific IDs
        val fullQuery = ChartTable.selectAll().where { ChartTable.id inList paginatedIds }

        // Decoupled join logic
        applyJoinsAndSelect(fullQuery, fetchAllVersions, fetchStreamingLinks)

        // Execute the query to get all chart data
        val results = fullQuery.toList()

        // Process results to group related entities (versions, contributors, etc.)
        val processedResults = processResultsInMemory(
            results,
            includeStreamingLinks = fetchStreamingLinks
        )

        // println("Fetched ${processedResults.size} with filters: userId=$userId, chartIds=${chartIds?.joinToString()}, search=$search, sortBy=$sortBy, difficulties=${difficulties?.joinToString()}, genres=${genres?.joinToString()}, limit=$limit, offset=$offset")

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
        fetchAllVersions: Boolean = false,
        fetchStreamingLinks: Boolean = false,
    ) {
        val columnsToSelect = mutableListOf<Column<*>>(*ChartTable.columns.toTypedArray())

        // Fetch contributors
        query.adjustColumnSet {
            leftJoin(ContributorTable, { ChartTable.id }, { ContributorTable.chartId })
                .leftJoin(UserTable, { ContributorTable.userId }, { UserTable.id })
        }
        columnsToSelect.addAll(ContributorTable.columns)
        columnsToSelect.addAll(UserTable.columns)

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

        if (fetchAllVersions) {
            query.adjustColumnSet {
                leftJoin(VersionTable, { ChartTable.id }, { VersionTable.chartId })
            }
            columnsToSelect.addAll(VersionTable.columns)
        } else {
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
        chartIds: List<ULong>?,
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

        // If user is provided, filter by their charts
        userId?.let {
            query.andWhere {
                ChartTable.id inSubQuery (
                    ContributorTable.select(ContributorTable.chartId)
                        .where { ContributorTable.userId eq it }
                )
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

        // Filter by difficulties - now using latest version
        difficulties?.takeIf { it.isNotEmpty() }?.let {
            query.andWhere {
                ChartTable.latestVersionId inSubQuery (
                        VersionTable.select(VersionTable.id)
                            .where { VersionTable.difficulty inList it }
                        )
            }
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
                }.distinctBy { it.id.value } // Use .value for ULong comparison
            } else emptyList()

            val versions = rows.mapNotNull { row ->
                row.getOrNull(VersionTable.id)?.let { versionId ->
                    VersionEntity.wrapRow(row)
                }
            }.distinctBy { it.id.value } // Use .value for ULong comparison

            val contributors = rows.mapNotNull { row ->
                // Check if contributor data exists
                row.getOrNull(ContributorTable.userId)?.let { userId ->
                    row.getOrNull(UserTable.id)?.let { userTableId ->
                        val contributor = ContributorEntity.wrapRow(row)
                        val user = UserEntity.wrapRow(row)
                        contributor to user
                    }
                }
            }.distinctBy { it.first.id.value } // Use the composite ID value for distinction

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
        chartIds: List<ULong>?,
        search: String?,
        sortBy: ChartSortOption?,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
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
            true
        )

        println("Fetched ${result.size} charts with filters: userId=$userId, chartIds=${chartIds?.joinToString()}, search=$search, sortBy=$sortBy, difficulties=${difficulties?.joinToString()}, genres=${genres?.joinToString()}, limit=$limit, offset=$offset")

        val charts = result.map { chartResult ->
            // println("Processing chart with ID: ${chartResult.chart.id.value}")
            daoToChart(
                entity = chartResult.chart,
                streamingLinks = null, // No streaming links for this variant
                versions = chartResult.versions.map { versionEntityToVersion(it) },
                contributors = chartResult.contributors.map {
                    contributorEntityToContributor(
                        it.component1(),
                        it.component2()
                    )
                },
            )
        }

        charts
    }

    // Mobile App Chart variant of getCharts that includes streaming links and only returns the latest version
    override suspend fun getCharts(
        chartIds: List<ULong>?,
        search: String?,
        sortBy: ChartSortOption?,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
        fetchStreamingLinks: Boolean,
    ): List<Chart> = newSuspendedTransaction {
        val result = fetchChartEntities(
            null,
            chartIds,
            search,
            sortBy,
            difficulties,
            genres,
            limit,
            offset,
            false,
            fetchStreamingLinks
        )

        val charts = result.map { chartResult ->
            // println("Processing chart with ID: ${chartResult.chart.id.value}")
            daoToChart(
                entity = chartResult.chart,
                streamingLinks = chartResult.streamingLinks?.map { daoToStreamingLink(it) },
                versions = chartResult.versions.map { versionEntityToVersion(it) },
                contributors = chartResult.contributors.map {
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

    override suspend fun createChart(
        userId: UUID,
        chart: CreateChartRequest,
    ): Chart = newSuspendedTransaction {
        if (chart.contentId.isNullOrBlank()) {
            throw BadRequestException("Share ID cannot be empty")
        }

        // exec("SET CONSTRAINTS chart_latest_version_id_fkey DEFERRED")

        // Create the content entry first
        val content = ContentEntity.new(chart.contentId) {
            this.type = ContentType.CHART
        }

        // Create the chart
        val newChart = ChartEntity.new(chart.id) {
            this.content = content
            this.artist = chart.artist
            this.track = chart.track
            this.album = chart.album
            this.genre = chart.genre
            this.trackPreviewUrl = chart.trackPreviewUrl
            this.coverUrl = chart.coverUrl
        }

        flushCache()

        /* HANDLING STREAMING LINKS ================ */

        val streamingLinks: MutableList<StreamingLinkEntity> = mutableListOf()

        // Handle streaming links with duplicate prevention
        val streamingLinkIds = chart.trackUrls.map { streamingLinkRequest ->
            // Try to find existing streaming link first
            val existingLink = StreamingLinkEntity.find {
                (StreamingLinkTable.platform eq streamingLinkRequest.platform) and
                        (StreamingLinkTable.url eq streamingLinkRequest.url)
            }.firstOrNull()

            if (existingLink != null) {
                // println("Using existing streaming link: ${existingLink.platform} - ${existingLink.url}")

                // Add existing link to the list
                streamingLinks.add(existingLink)

                // Use existing streaming link
                existingLink.id.value
            } else {
                // println("Creating new streaming link: ${streamingLinkRequest.platform} - ${streamingLinkRequest.url}")

                // Create new streaming link
                val newLink = StreamingLinkEntity.new {
                    this.platform = streamingLinkRequest.platform
                    this.url = streamingLinkRequest.url
                }

                // Add new link to the list
                streamingLinks.add(newLink)

                // Use new streaming link
                newLink.id.value
            }
        }

        // Link the chart to the streaming links through the junction table
        ChartStreamingLinkTable.batchInsert(streamingLinkIds) { streamingLinkId ->
            this[ChartStreamingLinkTable.chartId] = newChart.id
            this[ChartStreamingLinkTable.streamingLinkId] = streamingLinkId
        }

        /* HANDLING CONTRIBUTORS ================ */

        val contributorId = CompositeID {
            it[ContributorTable.chartId] = newChart.id
            it[ContributorTable.userId] = userId
        }

        // Add the user as an author of the chart
        val contributor = ContributorEntity.new(contributorId) {
            roles = listOf(ContributorRole.AUTHOR)
        }

        /* HANDLING INITIAL VERSION  ================ */

        // Add the initial version with the chart's metadata
        val initialVersion = VersionEntity.new(chart.versionId) {
            this.chart = newChart
            this.bundleUrl = chart.bundleUrl
            this.previewUrl = chart.previewUrl
            this.duration = chart.duration
            this.notesAmount = chart.notesAmount
            this.effectsAmount = chart.effectsAmount
            this.bpm = chart.bpm
            this.difficulty = chart.difficulty
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
        }

        ChartEntity.findByIdAndUpdate(newChart.id.value) {
            it.latestVersion = initialVersion
        }

        // newChart.latestVersion = initialVersion

        // Since the new chart will be cached on the creator device,
        // we need to return all the data to avoid inconsistencies
        daoToChart(
            newChart,
            streamingLinks = streamingLinks.map { daoToStreamingLink(it) },
            listOf(versionEntityToVersion(initialVersion)),
            listOf(contributorEntityToContributor(contributor)),
        )
    }

    override suspend fun updateChart(id: ULong, chart: UpdateChartRequest): Chart = newSuspendedTransaction {

        val existingChart = ChartEntity.findSingleByAndUpdate(ChartTable.id eq id) {
            it.artist = chart.artist ?: it.artist
            it.track = chart.track ?: it.track
            it.genre = chart.genre ?: it.genre
            it.coverUrl = chart.coverUrl ?: it.coverUrl
            it.isFeatured = chart.isFeatured ?: it.isFeatured
            it.isPublic = chart.isPublic ?: it.isPublic
        }

        // Update the content's updatedAt timestamp
        existingChart?.content?.updatedAt = LocalDateTime.now()

        if (existingChart == null) {
            throw NotFoundException("Chart with ID $id not found")
        }

        // TODO: It's not performant quite performant, but it works with the dashboard for now
        daoToChart(
            entity = existingChart,
            streamingLinks = existingChart.trackUrls.map { daoToStreamingLink(it) },
            contributors = existingChart.contributors.map { contributorEntityToContributor(it) },
            versions = existingChart.versions.map { versionEntityToVersion(it) }
        )
    }

    override suspend fun deleteChart(id: ULong): Boolean = newSuspendedTransaction {
        val chart = ChartEntity.findById(id) ?: return@newSuspendedTransaction false
        chart.delete()
        true
    }

    override suspend fun postAnalytics(chartId: ULong, action: AnalyticsOption): Boolean = newSuspendedTransaction {
        when (action) {
            AnalyticsOption.INSTALL_CONTENT, AnalyticsOption.UPDATE_CONTENT -> {
                // Handle download analytics
                ChartEntity.findByIdAndUpdate(chartId) { entity ->
                    entity.latestVersion?.let { version ->
                        version.downloadsAmount += 1
                    }
                }
                println("Download analytics recorded for chart $chartId")
                true
            }

            AnalyticsOption.DELETE_CONTENT -> throw NotFoundException("Cannot post analytics for deleted charts")
            AnalyticsOption.UPDATE_APP -> throw NotFoundException("Cannot post analytics for app updates")
        }
    }

    override suspend fun refreshChartsBundles(ids: Map<String, String>): Boolean  = newSuspendedTransaction {
        // Iterate through all chart IDs in a batch and refresh its bundle URL
        val sql = buildString {
            append("UPDATE ${VersionTable.tableName} SET ${VersionTable.bundleUrl.name} = CASE ${VersionTable.id.name} ")
            ids.forEach { (id, url) ->
                append("WHEN '$id' THEN '$url' ")
            }
            append("END WHERE ${VersionTable.id.name} IN (${ids.keys.joinToString { "'$it'" }});")
        }

        transaction {
            exec(sql)

            println("Successfully refreshed bundle URLs for ${ids.size} charts")
            true
        }

        /*val batchUpdate = BatchUpdateStatement(VersionTable)

        chartsIds.forEach { entry ->
            batchUpdate.addBatch(
            batchUpdate[VersionTable.bundleUrl] = entry.value
        }

        val result = batchUpdate.execute(TransactionManager.current())

        if (result == null) {
            println("No charts were updated. Check if the provided chart IDs are valid.")
            return@newSuspendedTransaction false
        }

        if (result > 0) {
            println("Successfully refreshed bundle URLs for ${chartsIds.size} charts")
            true
        } else {
            println("Failed to refresh bundle URLs for charts: $chartsIds")
            false
        }*/
    }
}
