package org.bscm.repository

import io.ktor.server.plugins.*
import io.ktor.util.logging.*
import org.bscm.models.Chart
import org.bscm.models.Contributor
import org.bscm.models.StreamingLink
import org.bscm.models.Version
import org.bscm.models.dao.*
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.*
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.mappers.VersionMapper.entityToVersion
import org.bscm.models.tables.*
import org.bscm.repository.ContributorRepository.Companion.contributorEntityToContributor
import org.bscm.utils.QueryUtils
import org.bscm.utils.UserStatsUtils
import org.bscm.utils.retryOnConflict
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import kotlin.math.max

private val log = KtorSimpleLogger("ChartRepository")

class ChartRepository : BaseRepository(), IChartRepository {


    /**
     * Calculates version indices for multiple versions efficiently using a window function.
     * Returns a map of versionId -> index (1-based).
     * This is much more efficient than calling calculateVersionIndex() for each version.
     */
    private suspend fun calculateVersionIndices(chartIds: List<ULong>): Map<ULong, Int> =
        newSuspendedTransaction {
            if (chartIds.isEmpty()) return@newSuspendedTransaction emptyMap()

            // Use raw SQL with window function for optimal performance
            val sql = """
                SELECT 
                    id,
                    ROW_NUMBER() OVER (PARTITION BY chart_id ORDER BY created_at) as version_index
                FROM ${VersionTable.tableName}
                WHERE chart_id IN (${chartIds.joinToString { it.toString() }})
            """.trimIndent()

            val result = mutableMapOf<ULong, Int>()
            exec(sql) { rs ->
                while (rs.next()) {
                    val versionId = rs.getLong("id").toULong()
                    val index = rs.getInt("version_index")
                    result[versionId] = index
                }
            }
            result
        }

    private class ChartResult(
        val chart: ChartEntity,
        val streamingLinks: List<StreamingLinkEntity>?,
        val versions: List<VersionEntity>,
        val contributors: List<Pair<ContributorEntity, UserEntity>>,
        var userStats: Pair<LocalDateTime?, LocalDateTime?> // (isLiked, isBookmarked)
    )

    data class ChartFilters(
        val userId: UUID? = null,
        val chartIds: List<ULong>? = null,
        val contentIds: List<String>? = null,
        val search: String? = null,
        val difficulties: List<Difficulty>? = null,
        val genres: List<Genre>? = null,
        val isDeluxe: Boolean? = null,
        val includePrivate: Boolean = false,
    )

    data class ChartAddons(
        val versions: Boolean = false,
        val streamingLinks: Boolean = false,
        val count: Boolean = false,
    )

    private fun toStreamingLink(
        entity: StreamingLinkEntity
    ): StreamingLink = StreamingLink(
        platform = entity.platform,
        url = entity.url,
    )

    private fun toChart(
        entity: ChartEntity,
        versions: List<Version>,
        contributors: List<Contributor>? = null,
        streamingLinks: List<StreamingLink>? = null,
        likedAt: LocalDateTime? = null,
        bookmarkedAt: LocalDateTime? = null,
    ): Chart {
        // We always expect at least one version to be present
        val latestVersion = versions.maxBy { it.createdAt }

        return Chart(
            id = entity.id.value.toString(),
            contentId = entity.contentId.value,
            track = entity.track,
            artist = entity.artist,
            album = entity.album,
            trackUrls = streamingLinks ?: emptyList(),
            trackPreviewUrl = entity.trackPreviewUrl,
            coverUrl = entity.coverUrl,
            isPublic = entity.isPublic,
            isFeatured = entity.isFeatured,
            genre = entity.genre,
            versions = if (versions.size > 1) versions else listOf(), // Only include versions list if there are multiple versions
            contributors = contributors ?: emptyList(),
            updatedAt = latestVersion.createdAt,
            createdAt = entity.createdAt,

            // Server-side computed fields
            downloadsSum = versions.sumOf { it.downloadsAmount },
            latestVersion = latestVersion,
            likedAt = likedAt,
            bookmarkedAt = bookmarkedAt,
        )
    }

    private suspend fun getChart(query: Query, addons: ChartAddons? = null): Chart? {
        // Apply joins based on addons
        applyJoinsAndSelect(
            query,
            fetchAllVersions = addons?.versions == true,
            fetchStreamingLinks = addons?.streamingLinks == true
        )

        // Process results
        val processedResults = processResultsInMemory(
            requestingUserId = getUserContext()?.userId,
            results = query.toList(),
            includeStreamingLinks = addons?.streamingLinks == true
        )

        if (processedResults.isEmpty()) return null

        val chartResult = processedResults.first()

        // Calculate version indices for this chart in a single query
        val versionIndices = calculateVersionIndices(listOf(chartResult.chart.id.value))

        return toChart(
            entity = chartResult.chart,
            streamingLinks = chartResult.streamingLinks?.map { toStreamingLink(it) },
            versions = chartResult.versions.map { entityToVersion(it, versionIndices[it.id.value] ?: 0) },
            contributors = chartResult.contributors.map {
                contributorEntityToContributor(it.component1(), it.component2())
            },
            likedAt = chartResult.userStats.first,
            bookmarkedAt = chartResult.userStats.second
        )
    }

    override suspend fun getChartById(id: ULong, addons: ChartAddons?): Chart? = newSuspendedTransaction {
        getChart(
            query = ChartTable.selectAll().where { ChartTable.id eq id },
            addons = addons
        )
    }

    override suspend fun getChartByContentId(contentId: String, addons: ChartAddons?): Chart? =
        newSuspendedTransaction {
            getChart(
                query = ChartTable.selectAll().where { ChartTable.contentId eq contentId },
                addons = addons
            )
        }

    private fun fetchChartEntities(
        sortBy: SortOption?,
        filters: ChartFilters? = null,
        addons: ChartAddons? = null,
        limit: Int? = 20,
        offset: Int? = null,
    ): Pair<List<ChartResult>, Int> {
        val startTime = System.currentTimeMillis()

        // First, fetch the correctly filtered and sorted IDs with pagination
        val baseQuery = ChartTable.select(ChartTable.id)

        // Then, apply filters and sorting
        applyFilters(query = baseQuery, filters = filters)
        applySorting(baseQuery, sortBy ?: SortOption.LAST_UPDATED)

        // println("Base query: ${baseQuery.prepareSQL(QueryBuilder(false))}")

        // Apply pagination
        limit?.takeIf { it > 0 }?.let { baseQuery.limit(it) }
        offset?.takeIf { it >= 0 }?.let { baseQuery.offset(it.toLong()) }

        val paginatedIds = baseQuery.map { it[ChartTable.id].value }
        if (paginatedIds.isEmpty()) {
            // println("No charts found with the provided filters.")
            return Pair(emptyList(), 0)
        }

        // println("Paginated IDs: ${paginatedIds.joinToString()}")

        // Now, fetch the full data for those specific IDs
        val fullQuery = ChartTable.selectAll().where { ChartTable.id inList paginatedIds }

        // Apply joins based on addons
        applyJoinsAndSelect(
            query = fullQuery,
            fetchAllVersions = addons?.versions == true,
            fetchStreamingLinks = addons?.streamingLinks == true
        )

        // Execute the query to get all chart data
        val results = fullQuery.toList()

        // Process results to group related entities (versions, contributors, etc.)
        // Use UserContext for user stats (independent of filtering)
        val processedResults = processResultsInMemory(
            requestingUserId = getUserContext()?.userId,
            results = results,
            includeStreamingLinks = addons?.streamingLinks == true
        )

        // println("Fetched ${processedResults.size} with filters: userId=${filters?.userId}, chartIds=${filters?.chartIds?.joinToString()}, search=${filters?.search}, sortBy=$sortBy, difficulties=${filters?.difficulties?.joinToString()}, genres=${filters?.genres?.joinToString()}, limit=$limit, offset=$offset")

        // The database does not guarantee order with an `IN` clause,
        // so we re-sort the results in memory based on the correctly ordered `paginatedIds`.
        val chartMap = processedResults.associateBy { it.chart.id.value }
        val sortedResults = paginatedIds.mapNotNull { id -> chartMap[id] }

        val endTime = System.currentTimeMillis()
        log.info("Charts fetch completed in ${endTime - startTime}ms with ${sortedResults.size} charts")

        return if (addons?.count == true) {
            Pair(sortedResults, baseQuery.count().toInt())
        } else {
            Pair(sortedResults, -1)
        }
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

    private fun applyFilters(query: Query, filters: ChartFilters?) {
        // Only return public charts if neither userId (contributor filter) nor includePrivate is set
        if (filters?.userId == null && filters?.includePrivate != true) {
            query.andWhere {
                ChartTable.isPublic eq true
            }
        }

        // If user is provided, filter by their charts
        filters?.userId?.let {
            query.andWhere {
                ChartTable.id inSubQuery (
                        ContributorTable.select(ContributorTable.chartId)
                            .where { ContributorTable.userId eq it }
                        )
            }
        }

        // Filter by a specific list of chartIds if provided
        filters?.chartIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            query.andWhere { ChartTable.id inList ids }
        }

        // Filter by a specific list of contentIds if provided
        filters?.contentIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            query.andWhere { ChartTable.contentId inList ids.map { it } }
        }

        // Search functionality for artist, track, or album
        if (!filters?.search.isNullOrBlank()) {
            applySearch(query, filters.search)
        }

        // Filter by difficulties - now using latest version
        filters?.difficulties?.takeIf { it.isNotEmpty() }?.let {
            query.andWhere {
                ChartTable.latestVersionId inSubQuery (
                        VersionTable.select(VersionTable.id)
                            .where { VersionTable.difficulty inList it }
                        )
            }
        }

        // Filter by genres
        filters?.genres?.takeIf { it.isNotEmpty() }?.let {
            query.andWhere { ChartTable.genre inList it }
        }

        // Filter by isDeluxe
        filters?.isDeluxe?.let { deluxe ->
            query.andWhere {
                ChartTable.latestVersionId inSubQuery (
                        VersionTable.select(VersionTable.id)
                            .where { VersionTable.isDeluxe eq deluxe }
                        )
            }
        }
    }

    private fun applySearch(query: Query, search: String) {
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

    private fun applySorting(query: Query, sortBy: SortOption) {
        when (sortBy) {
            SortOption.LAST_UPDATED -> {
                query
                    .adjustColumnSet {
                        innerJoin(VersionTable, { ChartTable.latestVersionId }, { VersionTable.id })
                    }
                    .orderBy(VersionTable.createdAt to SortOrder.DESC)
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

    /**
     * Fetches user interaction stats (likes and bookmarks) for a batch of charts.
     * Returns a map of contentId -> (isLiked, isBookmarked)
     */
    private fun fetchUserStats(
        userId: UUID?,
        groupedByChartId: Map<ULong, List<ResultRow>>
    ): Map<String, Pair<LocalDateTime?, LocalDateTime?>> {
        if (userId == null || groupedByChartId.isEmpty()) {
            return emptyMap()
        }

        // Extract all contentIds from the grouped charts
        val contentIds = groupedByChartId.values.map { rows -> rows.first()[ChartTable.contentId].value }

        if (contentIds.isEmpty()) {
            return emptyMap()
        }

        return UserStatsUtils.fetchUserStats(userId, contentIds)
    }

    private fun processResultsInMemory(
        requestingUserId: UUID? = null,
        results: List<ResultRow>,
        includeStreamingLinks: Boolean
    ): List<ChartResult> {
        // Group the rows by Chart ID
        val groupedByChartId = results.groupBy { it[ChartTable.id].value }

        // Pre-fetch user stats for all charts in this batch if requestingUserId is provided
        val userStats = fetchUserStats(requestingUserId, groupedByChartId)

        return groupedByChartId.map { (_, rows) ->
            val chartEntity = ChartEntity.wrapRow(rows.first())

            val streamingLinks = if (includeStreamingLinks) {
                rows.mapNotNull { row ->
                    // Check if streaming link data exists in this row
                    row.getOrNull(StreamingLinkTable.id)?.let { _ ->
                        // Also check if URL exists to ensure it's not a NULL join result
                        row.getOrNull(StreamingLinkTable.url)?.let {
                            StreamingLinkEntity.wrapRow(row)
                        }
                    }
                }.distinctBy { it.id.value }
            } else emptyList()

            val versions = rows.mapNotNull { row ->
                row.getOrNull(VersionTable.id)?.let { _ ->
                    VersionEntity.wrapRow(row)
                }
            }.distinctBy { it.id.value }

            val contributors = rows.mapNotNull { row ->
                // Check if contributor data exists
                row.getOrNull(ContributorTable.userId)?.let { _ ->
                    row.getOrNull(UserTable.id)?.let { _ ->
                        val contributor = ContributorEntity.wrapRow(row)
                        val user = UserEntity.wrapRow(row)
                        contributor to user
                    }
                }
            }.distinctBy { it.first.id.value }

            ChartResult(
                chart = chartEntity,
                streamingLinks = streamingLinks,
                versions = versions,
                contributors = contributors,
                userStats = userStats[chartEntity.contentId.value] ?: Pair(null, null)
            )
        }
    }

    override suspend fun getCharts(
        sortBy: SortOption?,
        filters: ChartFilters?,
        addons: ChartAddons?,
        limit: Int?,
        offset: Int?,
    ): Pair<List<Chart>, Int?> = newSuspendedTransaction {
        // Don't modify filters - userId in filters is for filtering charts (dashboard mode)
        // UserContext userId is separate and used only for fetching user stats
        val (results, total) = fetchChartEntities(
            sortBy = sortBy,
            filters = filters,
            addons = addons,
            limit = limit,
            offset = offset,
        )

        if (results.isEmpty()) {
            return@newSuspendedTransaction Pair(emptyList(), if (addons?.count == true) total else null)
        }

        val includeStreamingLinks = addons?.streamingLinks == true

        // Calculate version indices for ALL charts in a single batch query
        val chartIds = results.map { it.chart.id.value }
        val versionIndices = calculateVersionIndices(chartIds)

        val charts = results.map { chartResult ->
            toChart(
                entity = chartResult.chart,
                streamingLinks = if (includeStreamingLinks) chartResult.streamingLinks?.map { toStreamingLink(it) } else null,
                versions = chartResult.versions.map { entityToVersion(it, versionIndices[it.id.value] ?: 0) },
                contributors = chartResult.contributors.map {
                    /*log.debug(
                        "Processing contributor for chart {}: userId={}, roles={}",
                        chartResult.chart.id.value,
                        it.second.id.value,
                        it.first.roles.joinToString()
                    )*/
                    contributorEntityToContributor(it.component1(), it.component2())
                },
                likedAt = chartResult.userStats.first,
                bookmarkedAt = chartResult.userStats.second
            )
        }

        Pair(charts, if (addons?.count == true) total else null)
    }

    override suspend fun getChartsByContentIds(contentIds: List<String>, addons: ChartAddons?): List<Chart> = newSuspendedTransaction {
        if (contentIds.isEmpty()) return@newSuspendedTransaction emptyList()

        val query = ChartTable.selectAll().where { ChartTable.contentId inList contentIds }
        applyJoinsAndSelect(
            query = query,
            fetchAllVersions = addons?.versions == true,
            fetchStreamingLinks = addons?.streamingLinks == true
        )

        val results = query.toList()
        val processedResults = processResultsInMemory(
            requestingUserId = getUserContext()?.userId,
            results = results,
            includeStreamingLinks = addons?.streamingLinks == true
        )

        // Calculate version indices for all charts in a single batch query
        val chartIds = processedResults.map { it.chart.id.value }
        val versionIndices = calculateVersionIndices(chartIds)

        processedResults.map { chartResult ->
            toChart(
                entity = chartResult.chart,
                streamingLinks = chartResult.streamingLinks?.map { toStreamingLink(it) },
                versions = chartResult.versions.map { entityToVersion(it, versionIndices[it.id.value] ?: 0) },
                contributors = chartResult.contributors.map {
                    contributorEntityToContributor(it.component1(), it.component2())
                },
                likedAt = chartResult.userStats.first,
                bookmarkedAt = chartResult.userStats.second
            )
        }
    }

    override suspend fun getSuggestions(query: String, limit: Int): List<String> = newSuspendedTransaction {
        if (query.isBlank()) return@newSuspendedTransaction emptyList()

        val startTime = System.currentTimeMillis()

        val result = QueryUtils.getSearchMatches(query, max(20, limit))

        val endTime = System.currentTimeMillis()

        log.info("Suggestions returned in ${endTime - startTime}ms with ${result.size} results")
        result
    }

    override suspend fun createChart(
        userId: UUID,
        chart: CreateChartRequest,
    ): Chart = newSuspendedTransaction {
        if (chart.contentId.isNullOrBlank()) {
            throw BadRequestException("Content id cannot be empty")
        }

        // exec("SET CONSTRAINTS chart_latest_version_id_fkey DEFERRED")

        // Create the content entry first
        val content = retryOnConflict {
            ContentEntity.new(chart.contentId) {
                this.type = ContentType.CHART
            }
        }

        // Create the chart
        val newChart = ChartEntity.new(chart.id) {
            this.contentId = content.id
            this.artist = chart.artist
            this.track = chart.track
            this.album = chart.album
            this.genre = chart.genre
            this.trackPreviewUrl = chart.trackPreviewUrl
            this.coverUrl = chart.coverUrl
            this.authorId = UserEntity[userId].id
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
        toChart(
            entity = newChart,
            versions = listOf(entityToVersion(initialVersion, 1)), // Always index 1 for initial version
            contributors = listOf(contributorEntityToContributor(contributor)),
            streamingLinks = streamingLinks.map { toStreamingLink(it) },
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

        if (existingChart == null) {
            throw NotFoundException("Chart with ID $id not found")
        }

        // Calculate version indices in a single batch query
        val versionIndices = calculateVersionIndices(listOf(id))

        // TODO: It's not performant quite performant, but it works with the dashboard for now
        toChart(
            entity = existingChart,
            streamingLinks = existingChart.trackUrls.map { toStreamingLink(it) },
            contributors = existingChart.contributors.map { contributorEntityToContributor(it) },
            versions = existingChart.versions.map { entityToVersion(it, versionIndices[it.id.value] ?: 0) }
        )
    }

    override suspend fun deleteChartAndGetContentId(id: ULong): String? = newSuspendedTransaction {
        val chart = ChartEntity.findById(id) ?: return@newSuspendedTransaction null
        val contentId = chart.contentId.value
        chart.delete()
        contentId
    }


    override suspend fun postAnalytics(chartId: ULong, action: OperationOption): Boolean = newSuspendedTransaction {
        when (action) {
            OperationOption.INSTALL, OperationOption.UPDATE -> {
                // Handle download analytics
                ChartEntity.findByIdAndUpdate(chartId) { entity ->
                    entity.latestVersion?.let { version ->
                        version.downloadsAmount += 1
                    }
                }
                log.info("Download analytics recorded for chart $chartId")
                true
            }

            OperationOption.DELETE -> throw NotFoundException("Cannot post analytics for deleted charts")
        }
    }

    override suspend fun refreshChartsBundles(messages: Map<String, org.bscm.services.UploadService.RefreshData>): Boolean =
        newSuspendedTransaction {
            // messages: key is message/chart id, value has versionId and URLs
            // Update bundle URLs by versionId from values
            val bundleSql = buildString {
                append("UPDATE ${VersionTable.tableName} SET ${VersionTable.bundleUrl.name} = CASE ${VersionTable.id.name} ")
                messages.forEach { (_, data) ->
                    append("WHEN '${data.versionId}' THEN '${data.bundleUrl}' ")
                }
                append("END WHERE ${VersionTable.id.name} IN (${messages.values.joinToString { "'${it.versionId}'" }});")
            }

            // Update cover URLs for charts that have a cover URL and existing coverUrl contains 'cdn.discordapp.com'
            val withCovers = messages.values.filter { it.coverUrl != null }
            val coverSql = if (withCovers.isNotEmpty()) {
                buildString {
                    append("UPDATE ${ChartTable.tableName} SET ${ChartTable.coverUrl.name} = CASE ${ChartTable.id.name} ")
                    withCovers.forEach { data ->
                        // Get chart ID from version ID
                        append("WHEN (SELECT ${VersionTable.chartId.name} FROM ${VersionTable.tableName} WHERE ${VersionTable.id.name} = '${data.versionId}') THEN '${data.coverUrl}' ")
                    }
                    append("END WHERE ${ChartTable.id.name} IN (")
                    append("SELECT DISTINCT ${VersionTable.chartId.name} FROM ${VersionTable.tableName} WHERE ${VersionTable.id.name} IN (${withCovers.joinToString { "'${it.versionId}'" }})")
                    append(") AND ${ChartTable.coverUrl.name} LIKE 'https://cdn.discordapp.com%';")
                }
            } else null

            // Update audio URLs


            transaction {
                exec(bundleSql)
                if (coverSql != null) {
                    exec(coverSql)
                }

                log.info("Successfully refreshed bundle URLs for ${messages.size} charts")
                if (withCovers.isNotEmpty()) {
                    log.info("Successfully refreshed cover URLs for ${withCovers.size} charts (only where coverUrl contains 'cdn.discordapp.com')")
                }
                true
            }
        }
}
