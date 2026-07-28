package org.bscm.repository

import kotlinx.datetime.Clock
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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class TourPassRepository(
    private val chartRepository: IChartRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val storageService: StorageService,
) : ITourPassRepository {

    /**
     * Fetches aggregate likes/bookmarks counts for a batch of catalog items.
     * Returns a map of contentId -> (likesCount, bookmarksCount).
     * Must be called within a transaction.
     */
    private fun fetchAggregateStats(contentIds: List<String>): Map<String, Pair<Int, Int>> {
        if (contentIds.isEmpty()) return emptyMap()

        val statsRows = CollectionItemTable
            .innerJoin(CollectionTable, { CollectionItemTable.collectionId }, { CollectionTable.id })
            .select(CollectionItemTable.contentId, CollectionTable.kind, CollectionTable.userId)
            .where {
                (CollectionTable.kind inList listOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS, CollectionKind.USER)) and
                    (CollectionItemTable.contentId inList contentIds)
            }
            .toList()

        return contentIds.associateWith { contentId ->
            val rows = statsRows.filter { it[CollectionItemTable.contentId].value == contentId }
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
    ): TourPass {
        val catalogItem = CatalogItemEntity[entity.id.value]
        return TourPass(
            name = entity.name,
            description = entity.description,
            charts = charts,
            coverUrl = storageService.tourPassCoverUrl(entity.id.value),
            likesCount = likesCount,
            bookmarksCount = bookmarksCount,
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

        val charts = chartRepository.getChartsByContentIds(
            contentIds = chartIds,
            addons = ChartRepository.ChartAddons(streamingLinks = true),
            requestingUserId = userId,
        ).associateBy { it.id }

        return chartIds.mapNotNull { charts[it] }
    }

    private suspend fun buildTourPassFromEntity(entity: TourPassEntity, userId: UUID?): TourPass {
        val charts = loadChartsForTourPass(entity, userId)
        val stats = fetchAggregateStats(listOf(entity.id.value))
        val (likesCount, bookmarksCount) = stats[entity.id.value] ?: (0 to 0)
        return buildTourPass(entity, charts, likesCount, bookmarksCount)
    }

    override suspend fun getTourPasses(
        userId: UUID?,
        contentIds: List<String>?,
        search: String?,
        limit: Int?,
        offset: Int?,
    ): List<TourPass> = suspendTransaction {
        val pageSize = limit ?: 20
        val pageOffset = offset ?: 0

        val query = TourPassEntity.all()

        val result = if (contentIds != null && contentIds.isNotEmpty()) {
            query.toList().filter { it.id.value in contentIds }
        } else {
            query.limit(pageSize).offset(pageOffset.toLong()).toList()
        }

        val paged = if (contentIds != null && contentIds.isNotEmpty()) {
            result.drop(pageOffset).take(pageSize)
        } else {
            result
        }

        if (paged.isEmpty()) return@suspendTransaction emptyList()

        val allTourPassIds = paged.map { it.id.value }
        val allStats = fetchAggregateStats(allTourPassIds)

        val allChartIds = paged.flatMap { entity ->
            getOrderedChartIds(entity.id.value)
        }.distinct()

        val chartMap = if (allChartIds.isNotEmpty()) {
            chartRepository.getChartsByContentIds(
                contentIds = allChartIds,
                addons = ChartRepository.ChartAddons(streamingLinks = true),
                requestingUserId = userId,
            ).associateBy { it.id }
        } else {
            emptyMap()
        }

        paged.map { entity ->
            val charts = getOrderedChartIds(entity.id.value).mapNotNull { chartMap[it] }
            val (likesCount, bookmarksCount) = allStats[entity.id.value] ?: (0 to 0)
            buildTourPass(entity, charts, likesCount, bookmarksCount)
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
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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
        val entity = TourPassEntity.findByIdAndUpdate(id) { entity ->
            name?.let { entity.name = it }
            description?.let { entity.description = it }
            artist?.let { entity.artist = it }
        } ?: throw IllegalArgumentException("TourPass $id not found")

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

	override suspend fun updateDiscordCoordinates(catalogItemId: String, channelId: String, messageId: String) = suspendTransaction {
		catalogItemRepository.updateDiscordCoordinates(catalogItemId, channelId, messageId)
	}
}
