package org.bscm.repository

import io.ktor.server.plugins.*
import io.ktor.util.logging.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.Chart
import org.bscm.models.Version
import org.bscm.models.dao.*
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.*
import org.bscm.models.interfaces.IChangelogRepository
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.models.tables.*
import org.bscm.utils.QueryUtils
import org.bscm.utils.flushEntityCache
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.Clock

private val log = KtorSimpleLogger("ChartRepository")

class ChartRepository(
    private val trackRepository: TrackRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val versionRepository: IVersionRepository,
    private val albumRepository: AlbumRepository,
    private val changelogRepository: IChangelogRepository,
) : IChartRepository {

    private val queryBuilder = ChartQueryBuilder()
    private val resultAssembler = ChartResultAssembler(trackRepository, catalogItemRepository, albumRepository)

    data class ChartFilters(
        val userId: UUID? = null,
        val chartIds: List<String>? = null,
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

    private suspend fun getChart(query: Query, addons: ChartAddons?, requestingUserId: UUID?): Chart? {
        // Flush pending DAO writes so raw DSL queries see entities created/mutated
        // earlier in the same transaction (EntityCache is not flushed automatically
        // by hand-built Query.toList calls, unlike DAO reads like Entity.find).
        flushEntityCache()

        queryBuilder.applyJoinsAndSelect(query, fetchStreamingRefs = addons?.streamingLinks == true)

        val results = query.toList()
        val chartIds = results.map { it[ChartTable.id].value }.distinct()
        val changelogs = changelogRepository.getByCatalogItemIds(chartIds)

        val processedResults = resultAssembler.processResultsInMemory(
            requestingUserId = requestingUserId,
            results = results,
            includeStreamingRefs = addons?.streamingLinks == true,
            changelogs = changelogs,
        )

        val enrichedResults = enrichWithVersionData(processedResults)
        return enrichedResults.firstOrNull()?.let { resultAssembler.toChart(it) }
    }

    override suspend fun getChartById(id: String, addons: ChartAddons?, requestingUserId: UUID?): Chart? = suspendTransaction {
        getChart(
            query = ChartTable.selectAll().where { ChartTable.id eq id },
            addons = addons,
            requestingUserId = requestingUserId,
        )
    }

    private suspend fun fetchChartEntities(
        sortBy: SortOption?,
        filters: ChartFilters?,
        addons: ChartAddons?,
        limit: Int? = 20,
        offset: Int? = null,
        requestingUserId: UUID? = null,
    ): Pair<List<ChartResultAssembler.ChartResult>, Int> {
        val startTime = System.currentTimeMillis()

        // Flush any pending DAO writes so the following DSL queries see all committed data.
        flushEntityCache()

        val baseQuery = ChartTable
            .innerJoin(CatalogItemTable, { ChartTable.id }, { CatalogItemTable.id })
            .innerJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
            .select(ChartTable.id)

        queryBuilder.applyFilters(baseQuery, filters)
        queryBuilder.applySorting(baseQuery, sortBy ?: SortOption.LAST_UPDATED)

        if (limit != null && limit > 0) {
            baseQuery.limit(limit)
        }
        if (offset != null && offset >= 0) {
            baseQuery.offset(offset.toLong())
        }

        val paginatedIds = baseQuery.map { row -> row[ChartTable.id].value }
        if (paginatedIds.isEmpty()) return Pair(emptyList(), 0)

        val fullQuery = ChartTable.selectAll().where { ChartTable.id inList paginatedIds }
        queryBuilder.applyJoinsAndSelect(fullQuery, fetchStreamingRefs = addons?.streamingLinks == true)

        val results = fullQuery.toList()
        val changelogs = changelogRepository.getByCatalogItemIds(paginatedIds)
        val processedResults = resultAssembler.processResultsInMemory(
            requestingUserId = requestingUserId,
            results = results,
            includeStreamingRefs = addons?.streamingLinks == true,
            changelogs = changelogs,
        )

        val enrichedResults = enrichWithVersionData(processedResults)
        val chartMap = enrichedResults.associateBy { it.chart.id.value }
        val sortedResults = paginatedIds.mapNotNull { id -> chartMap[id] }

        val endTime = System.currentTimeMillis()
        log.info("Charts fetch completed in ${endTime - startTime}ms with ${sortedResults.size} charts")

        return if (addons?.count == true) {
            Pair(sortedResults, baseQuery.count().toInt())
        } else {
            Pair(sortedResults, -1)
        }
    }

    override suspend fun getCharts(
        sortBy: SortOption?,
        filters: ChartFilters?,
        addons: ChartAddons?,
        limit: Int?,
        offset: Int?,
        requestingUserId: UUID?,
    ): Pair<List<Chart>, Int?> = suspendTransaction {
        val (results, total) = fetchChartEntities(
            sortBy = sortBy,
            filters = filters,
            addons = addons,
            limit = limit,
            offset = offset,
            requestingUserId = requestingUserId,
        )

        if (results.isEmpty()) return@suspendTransaction Pair(emptyList(), if (addons?.count == true) total else null)

        val charts = results.map { resultAssembler.toChart(it) }
        Pair(charts, if (addons?.count == true) total else null)
    }

    override suspend fun getChartsByCatalogIds(catalogIds: List<String>, addons: ChartAddons?, requestingUserId: UUID?): List<Chart> =
        suspendTransaction {
            if (catalogIds.isEmpty()) return@suspendTransaction emptyList()

            flushEntityCache()

            val query = ChartTable.selectAll().where {
                ChartTable.id inList catalogIds
            }
            queryBuilder.applyJoinsAndSelect(query, fetchStreamingRefs = addons?.streamingLinks == true)

            val results = query.toList()
            val changelogs = changelogRepository.getByCatalogItemIds(catalogIds)
            val processedResults = resultAssembler.processResultsInMemory(
                requestingUserId = requestingUserId,
                results = results,
                includeStreamingRefs = addons?.streamingLinks == true,
                changelogs = changelogs,
            )
            enrichWithVersionData(processedResults).map { resultAssembler.toChart(it) }
        }

    private fun enrichWithVersionData(results: List<ChartResultAssembler.ChartResult>): List<ChartResultAssembler.ChartResult> {
        val catalogIds = results.map { it.chart.id.value }
        if (catalogIds.isEmpty()) return results

        // Single pass: fetch version rows once, derive both counts and latest in memory
        val allVersions = VersionTable.selectAll()
            .where { VersionTable.catalogItemId inList catalogIds }
            .toList()
            .groupBy { it[VersionTable.catalogItemId].value }

        return results.map { result ->
            val chartId = result.chart.id.value
            val latestRow = allVersions[chartId].orEmpty().maxByOrNull { it[VersionTable.versionCode] }
            val latestVersionEntity = latestRow?.let { VersionEntity.wrapRow(it) }
            result.copy(
                versionsCount = allVersions[chartId].orEmpty().size,
                latestVersion = latestVersionEntity,
                bundleHash = latestVersionEntity?.bundleHash,
            )
        }
    }

    override suspend fun getSuggestions(query: String, limit: Int): List<String> = suspendTransaction {
        if (query.isBlank()) return@suspendTransaction emptyList()

        val startTime = System.currentTimeMillis()
        val result = QueryUtils.getSearchMatches(query, kotlin.math.max(20, limit))
        val endTime = System.currentTimeMillis()

        log.info("Suggestions returned in ${endTime - startTime}ms with ${result.size} results")
        result
    }

    override suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart = suspendTransaction {
        val catalogItem = catalogItemRepository.create(
            type = CatalogItemType.CHART,
            authorId = userId,
            previewVideoId = null,
            catalogId = chart.catalogId,
        )

        val album = chart.albumId?.let { AlbumEntity.findById(it) }

        val track = trackRepository.findOrCreate(
            title = chart.track,
            artist = chart.artist,
            album = album,
            isrc = chart.isrc,
            genres = chart.genres,
            bpm = chart.bpm,
            duration = chart.duration,
        )

        trackRepository.attachStreamingRefs(track.id.value, chart.trackUrls)

        val newChart = ChartEntity.new(catalogItem.id.value) {
            this.track = track
            this.difficulty = chart.difficulty
            this.notesAmount = chart.notesAmount
            this.effectsAmount = chart.effectsAmount
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
        }

        ContributorEntity.new {
            this.catalogItem = CatalogItemEntity[catalogItem.id.value]
            this.user = UserEntity[userId]
            this.role = ContributorRole.AUTHOR
        }

        chart.contributors.forEach { contributor ->
            // The author is always added as AUTHOR above; skip duplicates to respect
            // the (catalogItemId, userId, role) unique index.
            if (contributor.role == ContributorRole.AUTHOR && contributor.userId == userId) {
                return@forEach
            }
            UserEntity.findById(contributor.userId)
                ?: throw IllegalArgumentException("User not found: ${contributor.userId}")

            val catalogItemEntityId = EntityID(catalogItem.id.value, CatalogItemTable)
            val existing = ContributorEntity.find {
                (ContributorTable.catalogItemId eq catalogItemEntityId) and
                    (ContributorTable.userId eq contributor.userId) and
                    (ContributorTable.role eq contributor.role)
            }.singleOrNull()

            if (existing == null) {
                ContributorEntity.new {
                    this.catalogItem = CatalogItemEntity[catalogItem.id.value]
                    this.user = UserEntity[contributor.userId]
                    this.role = contributor.role
                }
            }
        }

        val query = ChartTable.selectAll().where { ChartTable.id eq newChart.id.value }
        getChart(query, ChartAddons(streamingLinks = true), requestingUserId = userId)
            ?: throw IllegalStateException("Failed to load chart after creation")
    }

    override suspend fun findChartByBundleHash(hash: String): Chart? = suspendTransaction {
        val versionRow = VersionTable.selectAll()
            .where { VersionTable.bundleHash eq hash }
            .firstOrNull() ?: return@suspendTransaction null

        val catalogItemId = versionRow[VersionTable.catalogItemId].value
        getChart(
            query = ChartTable.selectAll().where { ChartTable.id eq catalogItemId },
            addons = ChartAddons(streamingLinks = false),
            requestingUserId = null,
        )
    }

    override suspend fun addVersion(catalogItemId: String, version: CreateVersionRequest, bundleHash: String): Version = suspendTransaction {
        versionRepository.addVersion(catalogItemId, version, bundleHash)
    }

    override suspend fun refreshChartsBundles(messages: Map<String, org.bscm.services.UploadService.RefreshData>): Boolean = true

    override suspend fun updateChart(id: String, chart: UpdateChartRequest, requestingUserId: UUID?): Chart = suspendTransaction<Chart> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val existingChart = ChartEntity.findSingleByAndUpdate(ChartTable.id eq id) {
            it.difficulty = chart.difficulty ?: it.difficulty
            it.isDeluxe = chart.isDeluxe ?: it.isDeluxe
            it.isExplicit = chart.isExplicit ?: it.isExplicit
        } ?: throw NotFoundException("Chart with ID $id not found")

        CatalogItemEntity.findByIdAndUpdate(id) {
            it.updatedAt = now
        }

        trackRepository.applyMetadataUpdates(
            track = existingChart.track,
            title = chart.track,
            artist = chart.artist,
            album = chart.album?.let { albumRepository.findOrCreate(it) },
            genres = chart.genres,
        )

        chart.isFeatured?.let { featured ->
            catalogItemRepository.updateFeatured(id, featured)
        }
        chart.visibility?.let { visibility ->
            catalogItemRepository.updateVisibility(id, visibility)
        }
        chart.previewVideoId?.let { previewVideoId ->
            catalogItemRepository.updatePreviewVideoId(id, previewVideoId)
        }

        val query = ChartTable.selectAll().where { ChartTable.id eq id }
        getChart(query, ChartAddons(streamingLinks = true), requestingUserId = requestingUserId)
            ?: throw IllegalStateException("Failed to load chart after update")
    }

    override suspend fun updateDiscordCoordinates(catalogItemId: String, channelId: String, messageId: String) = suspendTransaction {
        catalogItemRepository.updateDiscordCoordinates(catalogItemId, channelId, messageId)
    }

    override suspend fun deleteChart(id: String): Boolean = suspendTransaction {
        val catalogItem = CatalogItemEntity.findById(id) ?: return@suspendTransaction false
        catalogItem.delete()
        true
    }

    override suspend fun postAnalytics(chartId: String, action: OperationOption): Boolean = suspendTransaction {
        when (action) {
            OperationOption.INSTALL, OperationOption.UPDATE -> {
                val chart = ChartEntity.findById(chartId) ?: throw NotFoundException("Chart not found")
                catalogItemRepository.incrementDownloads(chart.id.value)
                log.info("Download analytics recorded for chart $chartId")
                true
            }

            OperationOption.DELETE -> throw NotFoundException("Cannot post analytics for deleted charts")
        }
    }
}
