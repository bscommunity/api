package org.bscm.repository

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.Chart
import org.bscm.models.StreamingRef
import org.bscm.models.TourPass
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.TourPassEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.CollectionKind
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.models.tables.*
import org.bscm.storage.StorageService
import org.bscm.utils.UserStatsUtils
import org.bscm.utils.flushEntityCache
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.Clock

class TourPassRepository(
    private val chartRepository: IChartRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val storageService: StorageService,
) : ITourPassRepository {

    /**
     * Fetches aggregate likes/bookmarks counts for a batch of catalog items.
     * Returns a map of catalogId -> (likesCount, bookmarksCount).
     * Must be called within a transaction.
     */
    private fun fetchAggregateStats(catalogIds: List<String>): Map<String, Pair<Int, Int>> {
        if (catalogIds.isEmpty()) return emptyMap()

        val statsRows = CollectionItemTable
            .innerJoin(CollectionTable, { CollectionItemTable.collectionId }, { CollectionTable.id })
            .select(CollectionItemTable.catalogId, CollectionTable.kind, CollectionTable.userId)
            .where {
                (CollectionTable.kind inList listOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS, CollectionKind.USER)) and
                    (CollectionItemTable.catalogId inList catalogIds)
            }
            .toList()

        return catalogIds.associateWith { catalogId ->
            val rows = statsRows.filter { it[CollectionItemTable.catalogId].value == catalogId }
            val likesCount = rows.filter { it[CollectionTable.kind] == CollectionKind.LIKES }
                .map { it[CollectionTable.userId] }
                .distinct()
                .size
            val bookmarksCount = rows.filter {
                it[CollectionTable.kind] == CollectionKind.BOOKMARKS || it[CollectionTable.kind] == CollectionKind.USER
            }
                .map { it[CollectionTable.userId] }
                .distinct()
                .size
            likesCount to bookmarksCount
        }
    }

    private fun buildTourPass(
        entity: TourPassEntity,
        charts: List<Chart>,
        likesCount: Int,
        bookmarksCount: Int,
        likedAt: LocalDateTime? = null,
        bookmarkedAt: LocalDateTime? = null,
    ): TourPass {
        val catalogItem = CatalogItemEntity[entity.id.value]
        return TourPass(
            name = entity.name,
            description = entity.description,
            charts = charts,
            coverUrl = storageService.tourPassCoverUrl(entity.id.value),
            likesCount = likesCount,
            bookmarksCount = bookmarksCount,
            likedAt = likedAt,
            bookmarkedAt = bookmarkedAt,
            contributors = emptyList(),
            createdAt = catalogItem.createdAt,
            publishedAt = catalogItem.publishedAt,
            updatedAt = catalogItem.updatedAt,
            id = entity.id.value,
            type = CatalogItemType.TOUR_PASS,
            status = catalogItem.status,
            visibility = catalogItem.visibility,
            isFeatured = catalogItem.isFeatured,
            downloadsSum = catalogItem.downloadsSum,
            previewVideoId = catalogItem.previewVideoId,
            discordChannelId = catalogItem.discordChannelId,
            discordMessageId = catalogItem.discordMessageId,
            authorId = catalogItem.author?.id?.value,
        )
    }

    private fun getOrderedChartIds(tourPassId: String): List<String> {
        return TourPassChartTable
            .select(TourPassChartTable.chartId)
            .where { TourPassChartTable.tourPassId eq EntityID(tourPassId, TourPassTable) }
            .orderBy(TourPassChartTable.position)
            .map { it[TourPassChartTable.chartId].value }
    }

    private suspend fun loadChartsForTourPass(entity: TourPassEntity, userId: UUID?): List<Chart> {
        val chartIds = getOrderedChartIds(entity.id.value)
        if (chartIds.isEmpty()) return emptyList()

        val charts = chartRepository.getChartsByCatalogIds(
            catalogIds = chartIds,
            addons = ChartRepository.ChartAddons(streamingLinks = true),
            requestingUserId = userId,
        ).associateBy { it.id }

        return chartIds.mapNotNull { charts[it] }
    }

    private suspend fun buildTourPassFromEntity(entity: TourPassEntity, userId: UUID?): TourPass {
        val charts = loadChartsForTourPass(entity, userId)
        val stats = fetchAggregateStats(listOf(entity.id.value))
        val (likesCount, bookmarksCount) = stats[entity.id.value] ?: (0 to 0)
        val (likedAt, bookmarkedAt) = if (userId != null) {
            UserStatsUtils.fetchUserStats(userId, listOf(entity.id.value))[entity.id.value] ?: (null to null)
        } else (null to null)
        return buildTourPass(entity, charts, likesCount, bookmarksCount, likedAt, bookmarkedAt)
    }

    override suspend fun getTourPasses(
        userId: UUID?,
        catalogIds: List<String>?,
        search: String?,
        limit: Int?,
        offset: Int?,
    ): List<TourPass> = suspendTransaction {
        val pageSize = limit ?: 20
        val pageOffset = offset ?: 0

        val query = TourPassTable.selectAll()

        when {
            catalogIds != null && catalogIds.isNotEmpty() && search != null -> {
                query.where {
                    (TourPassTable.id inList catalogIds.map { EntityID(it, TourPassTable) }) and
                    ((TourPassTable.name like "%${search}%") or (TourPassTable.description like "%${search}%"))
                }
            }
            catalogIds != null && catalogIds.isNotEmpty() -> {
                query.where { TourPassTable.id inList catalogIds.map { EntityID(it, TourPassTable) } }
            }
            search != null -> {
                query.where { (TourPassTable.name like "%${search}%") or (TourPassTable.description like "%${search}%") }
            }
        }

        query.orderBy(TourPassTable.id to SortOrder.DESC)
        val paged = query.limit(pageSize).offset(pageOffset.toLong()).toList()
            .map { TourPassEntity.wrapRow(it) }

        if (paged.isEmpty()) return@suspendTransaction emptyList()

        val allTourPassIds = paged.map { it.id.value }
        val allStats = fetchAggregateStats(allTourPassIds)

        val allChartIds = paged.flatMap { entity ->
            getOrderedChartIds(entity.id.value)
        }.distinct()

        val chartMap = if (allChartIds.isNotEmpty()) {
            chartRepository.getChartsByCatalogIds(
                catalogIds = allChartIds,
                addons = ChartRepository.ChartAddons(streamingLinks = true),
                requestingUserId = userId,
            ).associateBy { it.id }
        } else {
            emptyMap()
        }

        val userStats = if (userId != null && paged.isNotEmpty()) {
            UserStatsUtils.fetchUserStats(userId, allTourPassIds)
        } else emptyMap()

        paged.map { entity ->
            val charts = getOrderedChartIds(entity.id.value).mapNotNull { chartMap[it] }
            val (likesCount, bookmarksCount) = allStats[entity.id.value] ?: (0 to 0)
            val (likedAt, bookmarkedAt) = userStats[entity.id.value] ?: (null to null)
            buildTourPass(entity, charts, likesCount, bookmarksCount, likedAt, bookmarkedAt)
        }
    }

    override suspend fun getTourPassById(id: String, userId: UUID?): TourPass? = suspendTransaction {
        TourPassEntity.findById(id)?.let { buildTourPassFromEntity(it, userId) }
    }

    override suspend fun createTourPass(
        userId: UUID,
        name: String,
        description: String?,
        artist: String?,
        playlistUrls: List<StreamingRef>?,
        chartIds: List<String>?,
        id: String?,
    ): TourPass = suspendTransaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val catalogItem = if (id != null) {
            CatalogItemEntity.new(id) {
                this.type = CatalogItemType.TOUR_PASS
                this.status = CatalogItemStatus.PUBLISHED
                this.author = UserEntity[userId]
                this.updatedAt = now
            }
        } else {
            CatalogItemEntity.new {
                this.type = CatalogItemType.TOUR_PASS
                this.status = CatalogItemStatus.PUBLISHED
                this.author = UserEntity[userId]
                this.updatedAt = now
            }
        }

        val tourPass = TourPassEntity.new(catalogItem.id.value) {
            this.name = name
            this.description = description
            this.artist = artist
        }

        flushEntityCache()

        chartIds?.forEachIndexed { index, cid ->
            TourPassChartTable.insertIgnore {
                it[TourPassChartTable.tourPassId] = EntityID(tourPass.id.value, TourPassTable)
                it[TourPassChartTable.chartId] = EntityID(cid, ChartTable)
                it[TourPassChartTable.position] = index
            }
        }

        buildTourPassFromEntity(tourPass, userId)
    }

    override suspend fun updateTourPass(
        id: String,
        userId: UUID,
        name: String?,
        description: String?,
        artist: String?,
        chartIds: List<String>?,
    ): TourPass = suspendTransaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val entity = TourPassEntity.findByIdAndUpdate(id) { entity ->
            name?.let { entity.name = it }
            description?.let { entity.description = it }
            artist?.let { entity.artist = it }
        } ?: throw IllegalArgumentException("TourPass $id not found")

        CatalogItemEntity.findByIdAndUpdate(id) {
            it.updatedAt = now
        }

        flushEntityCache()

        chartIds?.let { newChartIds ->
            TourPassChartTable.deleteWhere { TourPassChartTable.tourPassId eq EntityID(id, TourPassTable) }
            newChartIds.forEachIndexed { index, cid ->
                TourPassChartTable.insertIgnore {
                    it[TourPassChartTable.tourPassId] = EntityID(id, TourPassTable)
                    it[TourPassChartTable.chartId] = EntityID(cid, ChartTable)
                    it[TourPassChartTable.position] = index
                }
            }
        }

        buildTourPassFromEntity(entity, userId)
    }

    override suspend fun deleteTourPass(id: String, userId: UUID): Boolean = suspendTransaction {
        CatalogItemEntity.findById(id)?.delete() ?: return@suspendTransaction false
        true
    }

    override suspend fun setTourPassCharts(id: String, userId: UUID, chartIds: List<String>): TourPass = suspendTransaction {
        TourPassEntity.findById(id) ?: throw IllegalArgumentException("TourPass $id not found")

        TourPassChartTable.deleteWhere { TourPassChartTable.tourPassId eq EntityID(id, TourPassTable) }
        chartIds.forEachIndexed { index, cid ->
            TourPassChartTable.insertIgnore {
                it[TourPassChartTable.tourPassId] = EntityID(id, TourPassTable)
                it[TourPassChartTable.chartId] = EntityID(cid, ChartTable)
                it[TourPassChartTable.position] = index
            }
        }

        buildTourPassFromEntity(TourPassEntity[id], userId)
    }

    override suspend fun addChartToTourPass(tourPassId: String, chartId: String): Boolean = suspendTransaction {
        TourPassChartTable.insertIgnore {
            it[TourPassChartTable.tourPassId] = EntityID(tourPassId, TourPassTable)
            it[TourPassChartTable.chartId] = EntityID(chartId, ChartTable)
        }.insertedCount > 0
    }

    override suspend fun removeChartFromTourPass(tourPassId: String, chartId: String): Boolean = suspendTransaction {
        TourPassChartTable.deleteWhere {
            (TourPassChartTable.tourPassId eq EntityID(tourPassId, TourPassTable)) and
                (TourPassChartTable.chartId eq EntityID(chartId, ChartTable))
        } > 0
    }

    override suspend fun countTourPasses(search: String?): Int = suspendTransaction {
        val query = TourPassTable.select(TourPassTable.id.count())
        search?.let { s ->
            query.where { (TourPassTable.name like "%${s}%") or (TourPassTable.description like "%${s}%") }
        }
        query.toList().first()[TourPassTable.id.count()].toInt()
    }

	override suspend fun updateDiscordCoordinates(catalogItemId: String, channelId: String, messageId: String) = suspendTransaction {
		catalogItemRepository.updateDiscordCoordinates(catalogItemId, channelId, messageId)
	}
}
