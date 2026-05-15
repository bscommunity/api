package org.bscm.repository

import io.ktor.server.plugins.*
import io.ktor.util.logging.*
import org.bscm.models.Chart
import org.bscm.models.dao.ChartEntity
import org.bscm.models.dao.ContributorEntity
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.*
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.tables.CatalogItemTable
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContributorTable
import org.bscm.models.tables.TrackTable
import org.bscm.utils.QueryUtils
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

private val log = KtorSimpleLogger("ChartRepository")

class ChartRepository(
    private val trackRepository: TrackRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val versionRepository: VersionRepository,
) : BaseRepository(), IChartRepository {

    private val queryBuilder = ChartQueryBuilder()
    private val resultAssembler = ChartResultAssembler(trackRepository, catalogItemRepository)

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

    private suspend fun getChart(query: Query, addons: ChartAddons?): Chart? {
        queryBuilder.applyJoinsAndSelect(query, fetchStreamingRefs = addons?.streamingLinks == true)

        val processedResults = resultAssembler.processResultsInMemory(
            requestingUserId = getUserContext()?.userId,
            results = query.toList(),
            includeStreamingRefs = addons?.streamingLinks == true,
        )

        return processedResults.firstOrNull()?.let { resultAssembler.toChart(it) }
    }

    override suspend fun getChartById(id: ULong, addons: ChartAddons?): Chart? = newSuspendedTransaction {
        getChart(
            query = ChartTable.selectAll().where { ChartTable.id eq id },
            addons = addons,
        )
    }

    override suspend fun getChartByContentId(contentId: String, addons: ChartAddons?): Chart? =
        newSuspendedTransaction {
            getChart(
                query = ChartTable.selectAll().where {
                    ChartTable.catalogItemId eq EntityID(contentId, CatalogItemTable)
                },
                addons = addons,
            )
        }

    private suspend fun fetchChartEntities(
        sortBy: SortOption?,
        filters: ChartFilters?,
        addons: ChartAddons?,
        limit: Int? = 20,
        offset: Int? = null,
    ): Pair<List<ChartResultAssembler.ChartResult>, Int> {
        val startTime = System.currentTimeMillis()

        val baseQuery = (ChartTable innerJoin CatalogItemTable innerJoin TrackTable)
            .select(ChartTable.id)

        queryBuilder.applyFilters(baseQuery, filters)
        queryBuilder.applySorting(baseQuery, sortBy ?: SortOption.LAST_UPDATED)

        limit?.takeIf { it > 0 }?.let { baseQuery.limit(it) }
        offset?.takeIf { it >= 0 }?.let { baseQuery.offset(it.toLong()) }

        val paginatedIds = baseQuery.map { it[ChartTable.id].value }
        if (paginatedIds.isEmpty()) return Pair(emptyList(), 0)

        val fullQuery = ChartTable.selectAll().where { ChartTable.id inList paginatedIds }
        queryBuilder.applyJoinsAndSelect(fullQuery, fetchStreamingRefs = addons?.streamingLinks == true)

        val results = fullQuery.toList()
        val processedResults = resultAssembler.processResultsInMemory(
            requestingUserId = getUserContext()?.userId,
            results = results,
            includeStreamingRefs = addons?.streamingLinks == true,
        )

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

    override suspend fun getCharts(
        sortBy: SortOption?,
        filters: ChartFilters?,
        addons: ChartAddons?,
        limit: Int?,
        offset: Int?,
    ): Pair<List<Chart>, Int?> = newSuspendedTransaction {
        val (results, total) = fetchChartEntities(
            sortBy = sortBy,
            filters = filters,
            addons = addons,
            limit = limit,
            offset = offset,
        )

        if (results.isEmpty()) return@newSuspendedTransaction Pair(emptyList(), if (addons?.count == true) total else null)

        val charts = results.map { resultAssembler.toChart(it) }
        Pair(charts, if (addons?.count == true) total else null)
    }

    override suspend fun getChartsByContentIds(contentIds: List<String>, addons: ChartAddons?): List<Chart> =
        newSuspendedTransaction {
            if (contentIds.isEmpty()) return@newSuspendedTransaction emptyList()

            val query = ChartTable.selectAll().where {
                ChartTable.catalogItemId inList contentIds.map { EntityID(it, CatalogItemTable) }
            }
            queryBuilder.applyJoinsAndSelect(query, fetchStreamingRefs = addons?.streamingLinks == true)

            val results = query.toList()
            resultAssembler.processResultsInMemory(
                requestingUserId = getUserContext()?.userId,
                results = results,
                includeStreamingRefs = addons?.streamingLinks == true,
            ).map { resultAssembler.toChart(it) }
        }

    override suspend fun getSuggestions(query: String, limit: Int): List<String> = newSuspendedTransaction {
        if (query.isBlank()) return@newSuspendedTransaction emptyList()

        val startTime = System.currentTimeMillis()
        val result = QueryUtils.getSearchMatches(query, kotlin.math.max(20, limit))
        val endTime = System.currentTimeMillis()

        log.info("Suggestions returned in ${endTime - startTime}ms with ${result.size} results")
        result
    }

    override suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart = newSuspendedTransaction {
        val catalogItem = catalogItemRepository.create(
            type = org.bscm.models.enums.CatalogItemType.CHART,
            authorId = userId,
            previewVideoId = null,
            contentId = chart.contentId,
        )

        val track = trackRepository.findOrCreate(
            title = chart.track,
            artist = chart.artist,
            album = chart.album,
            isrc = null,
            genre = chart.genre,
            bpm = chart.bpm,
            duration = chart.duration,
        )

        trackRepository.attachStreamingRefs(track.id.value, chart.trackUrls)

        val newChart = ChartEntity.new(chart.id) {
            this.catalogItem = catalogItem
            this.track = track
            this.difficulty = chart.difficulty
            this.notesAmount = chart.notesAmount
            this.effectsAmount = chart.effectsAmount
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
        }

        val contributorId = CompositeID {
            it[ContributorTable.chartId] = newChart.id
            it[ContributorTable.userId] = userId
        }

        ContributorEntity.new(contributorId) {
            roles = listOf(ContributorRole.AUTHOR)
        }

        versionRepository.addVersion(
            catalogItemId = catalogItem.id.value,
            version = CreateVersionRequest(
                id = chart.versionId,
                track = chart.track,
                artist = chart.artist,
                duration = chart.duration,
                notesAmount = chart.notesAmount,
                effectsAmount = chart.effectsAmount,
                bpm = chart.bpm,
                difficulty = chart.difficulty,
                isDeluxe = chart.isDeluxe,
                isExplicit = chart.isExplicit,
                bundleUrl = chart.bundleUrl,
                previewUrl = chart.previewUrl,
                fileSizeBytes = chart.fileSizeBytes,
            )
        )

        val query = ChartTable.selectAll().where { ChartTable.id eq newChart.id.value }
        getChart(query, ChartAddons(streamingLinks = true))
            ?: throw IllegalStateException("Failed to load chart after creation")
    }

    override suspend fun updateChart(id: ULong, chart: UpdateChartRequest): Chart = newSuspendedTransaction<Chart> {
        val existingChart = ChartEntity.findSingleByAndUpdate(ChartTable.id eq id) {
            it.difficulty = chart.difficulty ?: it.difficulty
            it.isDeluxe = chart.isDeluxe ?: it.isDeluxe
            it.isExplicit = chart.isExplicit ?: it.isExplicit
        } ?: throw NotFoundException("Chart with ID $id not found")

        trackRepository.applyMetadataUpdates(
            track = existingChart.track,
            title = chart.track,
            artist = chart.artist,
            album = chart.album,
            genre = chart.genre,
        )

        chart.isFeatured?.let { featured ->
            catalogItemRepository.updateFeatured(existingChart.catalogItem.id.value, featured)
        }
        chart.isPublic?.let { isPublic ->
            catalogItemRepository.updateVisibility(existingChart.catalogItem.id.value, isPublic)
        }

        val query = ChartTable.selectAll().where { ChartTable.id eq id }
        getChart(query, ChartAddons(streamingLinks = true))
            ?: throw IllegalStateException("Failed to load chart after update")
    }

    override suspend fun deleteChartAndGetContentId(id: ULong): String? = newSuspendedTransaction {
        val chart = ChartEntity.findById(id) ?: return@newSuspendedTransaction null
        val contentId = chart.catalogItem.id.value
        chart.delete()
        contentId
    }

    override suspend fun postAnalytics(chartId: ULong, action: OperationOption): Boolean = newSuspendedTransaction {
        when (action) {
            OperationOption.INSTALL, OperationOption.UPDATE -> {
                val chart = ChartEntity.findById(chartId) ?: throw NotFoundException("Chart not found")
                catalogItemRepository.incrementDownloads(chart.catalogItem.id.value)
                log.info("Download analytics recorded for chart $chartId")
                true
            }

            OperationOption.DELETE -> throw NotFoundException("Cannot post analytics for deleted charts")
        }
    }

    override suspend fun refreshChartsBundles(
        messages: Map<String, org.bscm.services.UploadService.RefreshData>
    ): Boolean = newSuspendedTransaction {
        if (messages.isNotEmpty()) {
            log.warn("refreshChartsBundles is deprecated with the new storage model; no changes applied")
        }
        true
    }
}
