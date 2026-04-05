package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.Chart
import org.bscm.models.StreamingLink
import org.bscm.models.TourPass
import org.bscm.models.dao.ContentEntity
import org.bscm.models.dao.TourPassEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.enums.ContentType
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.models.tables.TourPassChartTable
import org.bscm.models.tables.TourPassTable
import org.bscm.utils.StreamingPlatformUtils
import org.bscm.utils.UserStatsUtils
import org.bscm.utils.retryOnConflict
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class TourPassRepository(
    private val chartRepository: IChartRepository
) : BaseRepository(), ITourPassRepository {

    private fun daoToTourPass(
        entity: TourPassEntity,
        charts: List<Chart>,
        likedAt: LocalDateTime? = null,
        bookmarkedAt: LocalDateTime? = null
    ): TourPass {
        val playlistUrls = entity.playlistUrls
            ?.takeIf { it.isNotBlank() }
            ?.let { StreamingPlatformUtils.deserializeLinks(it) }
            ?: emptyList()

        return TourPass(
            id = entity.id.value.toString(),
            contentId = entity.content.id.value,
            name = entity.name,
            description = entity.description,
            artist = entity.artist,
            coverUrl = entity.coverUrl,
            charts = charts,
            playlistUrls = playlistUrls,
            isPublic = entity.isPublic,
            isFeatured = entity.isFeatured,
            likedAt = likedAt,
            bookmarkedAt = bookmarkedAt,
            downloadsSum = entity.downloadsSum,
            createdAt = entity.createdAt,
            updatedAt = entity.latestPublishedAt ?: LocalDateTime.now()
        )
    }

    override suspend fun getTourPasses(
        userId: UUID?,
        contentIds: List<String>?,
        search: String?,
        limit: Int?,
        offset: Int?
    ): List<TourPass> = newSuspendedTransaction {
        val query = TourPassTable.selectAll()

        // Anonymous users see only public items; authenticated users also see their own private items.
        query.andWhere {
            if (userId == null) {
                TourPassTable.isPublic eq true
            } else {
                (TourPassTable.isPublic eq true) or (TourPassTable.authorId eq userId)
            }
        }

        if (!contentIds.isNullOrEmpty()) {
            query.andWhere { TourPassTable.contentId inList contentIds }
        }

        if (!search.isNullOrBlank()) {
            query.andWhere {
                (TourPassTable.name like "%$search%") or
                        (TourPassTable.artist like "%$search%")
            }
        }

        // Order by latest published at descending
        query.orderBy(TourPassTable.latestUpdatedAt to SortOrder.DESC)

        if (limit != null) {
            query.limit(limit).offset(offset?.toLong() ?: 0)
        }

        val tourPassEntities = TourPassEntity.wrapRows(query).toList()

        // Fetch user stats for all tour passes in one query
        val tourPassContentIds = tourPassEntities.map { it.content.id.value }
        val userStats = UserStatsUtils.fetchUserStats(getUserContext()?.userId, tourPassContentIds)

        tourPassEntities.map { tourPassEntity ->
            val charts = getChartsForTourPass(tourPassEntity.id.value, null)
            val contentId = tourPassEntity.content.id.value
            val stats = userStats[contentId] ?: Pair(null, null)
            daoToTourPass(tourPassEntity, charts, stats.first, stats.second)
        }
    }

    override suspend fun getTourPassById(id: ULong, userId: UUID?): TourPass? = newSuspendedTransaction {
        val entity = TourPassEntity.findById(id) ?: return@newSuspendedTransaction null
        if (!entity.isPublic && entity.authorId.value != userId) {
            return@newSuspendedTransaction null
        }
        val charts = getChartsForTourPass(id, null)
        daoToTourPass(entity, charts)
    }

    override suspend fun getAppTourPassById(contentId: String, userId: UUID?): TourPass? = newSuspendedTransaction {
        val entity = TourPassEntity.find { TourPassTable.contentId eq contentId }.firstOrNull()
            ?: return@newSuspendedTransaction null
        if (!entity.isPublic && entity.authorId.value != userId) {
            return@newSuspendedTransaction null
        }
        val charts = getChartsForTourPass(entity.id.value, null)
        daoToTourPass(entity, charts)
    }

    override suspend fun createTourPass(
        userId: UUID,
        name: String,
        description: String?,
        artist: String?,
        coverUrl: String,
        playlistUrls: List<StreamingLink>?,
        chartIds: List<ULong>?,
        id: ULong?
    ): TourPass = newSuspendedTransaction {
        // Create new content entry
        val content = retryOnConflict {
            ContentEntity.new {
                this.type = ContentType.TOUR_PASS
            }
        }

        val entity = if (id != null) {
            TourPassEntity.new(id) {
                this.content = content
                this.name = name
                this.description = description
                this.artist = artist
                this.coverUrl = coverUrl
                this.playlistUrls = playlistUrls
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { StreamingPlatformUtils.serializeLinks(it) }
                this.isPublic = true
                this.isFeatured = false
                this.downloadsSum = 0
                this.latestPublishedAt = LocalDateTime.now()
                this.authorId = UserEntity[userId].id
            }
        } else {
            TourPassEntity.new {
                this.content = content
                this.name = name
                this.description = description
                this.artist = artist
                this.coverUrl = coverUrl
                this.playlistUrls = playlistUrls
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { StreamingPlatformUtils.serializeLinks(it) }
                this.isPublic = true
                this.isFeatured = false
                this.downloadsSum = 0
                this.latestPublishedAt = LocalDateTime.now()
                this.authorId = UserEntity[userId].id
            }
        }

        chartIds?.let { ids ->
            replaceTourPassCharts(entity.id.value, ids)
            updateTourPassStats(entity.id.value)
        }

        daoToTourPass(entity, emptyList())
    }

    override suspend fun updateTourPass(
        id: ULong,
        userId: UUID,
        name: String?,
        description: String?,
        artist: String?,
        coverUrl: String?,
        playlistUrls: List<StreamingLink>?,
        chartIds: List<ULong>?
    ): TourPass = newSuspendedTransaction {
        val entity = TourPassEntity.findById(id)
            ?.takeIf { it.authorId.value == userId }
            ?: throw NotFoundException("TourPass not found")

        name?.let { entity.name = it }
        description?.let { entity.description = it }
        artist?.let { entity.artist = it }
        coverUrl?.let { entity.coverUrl = it }
        if (playlistUrls != null) {
            entity.playlistUrls = playlistUrls
                .takeIf { it.isNotEmpty() }
                ?.let { StreamingPlatformUtils.serializeLinks(it) }
        }

        chartIds?.let { ids ->
            replaceTourPassCharts(id, ids)
            updateTourPassStats(id)
        }

        val charts = getChartsForTourPass(id, null)
        daoToTourPass(entity, charts)
    }

    override suspend fun setTourPassCharts(id: ULong, userId: UUID, chartIds: List<ULong>): TourPass = newSuspendedTransaction {
        val entity = TourPassEntity.findById(id)
            ?.takeIf { it.authorId.value == userId }
            ?: throw NotFoundException("TourPass not found")

        replaceTourPassCharts(id, chartIds)
        updateTourPassStats(id)

        val charts = getChartsForTourPass(id, null)
        daoToTourPass(entity, charts)
    }

    override suspend fun deleteTourPass(id: ULong, userId: UUID): Boolean = newSuspendedTransaction {
        val entity = TourPassEntity.findById(id)
            ?.takeIf { it.authorId.value == userId }
            ?: return@newSuspendedTransaction false
        entity.delete()
        true
    }

    private suspend fun getChartsForTourPass(tourPassId: ULong, userId: UUID?): List<Chart> {
        val chartIds = TourPassChartTable
            .selectAll()
            .where { TourPassChartTable.tourPassId eq tourPassId }
            .orderBy(TourPassChartTable.position to SortOrder.ASC)
            .map { it[TourPassChartTable.chartId].value }

        if (chartIds.isEmpty()) return emptyList()

        // Use the real ChartRepository to get full chart details with versions, contributors, etc.
        return chartRepository.getCharts(
            filters = ChartRepository.ChartFilters(
                chartIds = chartIds,
                userId = userId
            ),
            addons = ChartRepository.ChartAddons(
                versions = true,
                streamingLinks = true,
            )
        ).first
    }

    override suspend fun addChartToTourPass(tourPassId: ULong, chartId: ULong): Boolean = newSuspendedTransaction {
        // Check if tour pass exists
        TourPassEntity.findById(tourPassId) ?: return@newSuspendedTransaction false

        // Check if chart is already in tour pass
        val existing = TourPassChartTable.selectAll()
            .where { (TourPassChartTable.tourPassId eq tourPassId) and (TourPassChartTable.chartId eq chartId) }
            .firstOrNull()

        if (existing != null) return@newSuspendedTransaction false

        // Add chart to tour pass
        val nextPosition = TourPassChartTable
            .select(TourPassChartTable.position.max())
            .where { TourPassChartTable.tourPassId eq tourPassId }
            .firstOrNull()
            ?.get(TourPassChartTable.position.max())
            ?.toString()
            ?.toIntOrNull()
            ?.let { it + 1 }
            ?: 0

        TourPassChartTable.insert {
            it[TourPassChartTable.tourPassId] = tourPassId
            it[TourPassChartTable.chartId] = chartId
            it[TourPassChartTable.position] = nextPosition
        }

        // Update tour pass statistics
        updateTourPassStats(tourPassId)
        true
    }

    override suspend fun removeChartFromTourPass(tourPassId: ULong, chartId: ULong): Boolean = newSuspendedTransaction {
        val deletedCount = TourPassChartTable.deleteWhere {
            (TourPassChartTable.tourPassId eq tourPassId) and
                    (TourPassChartTable.chartId eq chartId)
        }

        if (deletedCount > 0) {
            reindexTourPassChartPositions(tourPassId)
            updateTourPassStats(tourPassId)
            true
        } else {
            false
        }
    }

    private fun replaceTourPassCharts(tourPassId: ULong, chartIds: List<ULong>) {
        TourPassChartTable.deleteWhere { TourPassChartTable.tourPassId eq tourPassId }

        chartIds.distinct().forEachIndexed { index, chartId ->
            TourPassChartTable.insert {
                it[TourPassChartTable.tourPassId] = tourPassId
                it[TourPassChartTable.chartId] = chartId
                it[TourPassChartTable.position] = index
            }
        }
    }

    private fun reindexTourPassChartPositions(tourPassId: ULong) {
        val chartIds = TourPassChartTable
            .select(TourPassChartTable.chartId, TourPassChartTable.position)
            .where { TourPassChartTable.tourPassId eq tourPassId }
            .orderBy(TourPassChartTable.position to SortOrder.ASC)
            .map { it[TourPassChartTable.chartId].value }

        chartIds.forEachIndexed { index, chartId ->
            TourPassChartTable.update(
                where = {
                    (TourPassChartTable.tourPassId eq tourPassId) and
                        (TourPassChartTable.chartId eq chartId)
                }
            ) {
                it[TourPassChartTable.position] = index
            }
        }
    }

    private suspend fun updateTourPassStats(tourPassId: ULong) = newSuspendedTransaction {
        val charts = getChartsForTourPass(tourPassId, null)

        TourPassEntity.findByIdAndUpdate(tourPassId) { entity ->
            entity.downloadsSum = charts.sumOf { it.downloadsSum }
            entity.latestPublishedAt = charts.maxOfOrNull { it.updatedAt } ?: LocalDateTime.now()
        }
    }
}
