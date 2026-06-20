package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.StreamingRef
import org.bscm.models.TourPass
import org.bscm.models.Track
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.TourPassEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.TourPassChartTable
import org.bscm.models.tables.TourPassTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class TourPassRepository(
    private val chartRepository: IChartRepository,
) : ITourPassRepository {

    private fun tourPassEntityToTourPass(entity: TourPassEntity): TourPass {
        val catalogItem = CatalogItemEntity[entity.id.value]
        return TourPass(
            name = entity.name,
            description = entity.description,
            charts = entity.charts.map { chartEntity ->
                Chart(
                    id = chartEntity.id.value,
                    type = CatalogItemType.CHART,
                    status = catalogItem.status,
                    visibility = catalogItem.visibility,
                    isFeatured = catalogItem.isFeatured,
                    downloadsSum = catalogItem.downloadsSum,
                    contributors = emptyList(),
                    createdAt = catalogItem.createdAt,
                    publishedAt = catalogItem.publishedAt,
                    updatedAt = catalogItem.updatedAt,
                    likedAt = null,
                    bookmarkedAt = null,
                    previewVideoId = catalogItem.previewVideoId,
                    discordChannelId = catalogItem.discordChannelId,
                    discordMessageId = catalogItem.discordMessageId,
                    authorId = catalogItem.author?.id?.value,
                    track = Track(
                        id = chartEntity.track.id.value,
                        title = chartEntity.track.title,
                        artist = chartEntity.track.artist,
                        album = chartEntity.track.album,
                        isrc = chartEntity.track.isrc,
                        genre = chartEntity.track.genre,
                        bpm = chartEntity.track.bpm,
                        duration = chartEntity.track.duration,
                        streamingRefs = emptyList(),
                    ),
                    versionsCount = catalogItem.versionsCount,
                    difficulty = chartEntity.difficulty,
                    notesAmount = chartEntity.notesAmount,
                    effectsAmount = chartEntity.effectsAmount,
                    isDeluxe = chartEntity.isDeluxe,
                    isExplicit = chartEntity.isExplicit,
                    latestVersion = catalogItem.latestVersion?.let {
                        org.bscm.models.mappers.VersionMapper.entityToVersion(it)
                    },
                )
            },
            coverId = entity.coverId,
            contributors = emptyList(),
            createdAt = catalogItem.createdAt,
            publishedAt = catalogItem.publishedAt,
            updatedAt = catalogItem.updatedAt,
            likedAt = null,
            bookmarkedAt = null,
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

    override suspend fun getTourPasses(
        userId: UUID?,
        contentIds: List<String>?,
        search: String?,
        limit: Int?,
        offset: Int?,
    ): List<TourPass> = newSuspendedTransaction {
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
        paged.map { tourPassEntityToTourPass(it) }
    }

    override suspend fun getTourPassById(id: String, userId: UUID?): TourPass? = newSuspendedTransaction {
        TourPassEntity.findById(id)?.let { tourPassEntityToTourPass(it) }
    }

    override suspend fun createTourPass(
        userId: UUID,
        name: String,
        description: String?,
        artist: String?,
        coverUrl: String,
        playlistUrls: List<StreamingRef>?,
        chartIds: List<String>?,
        id: String?,
    ): TourPass = newSuspendedTransaction {
        val catalogItem = if (id != null) {
            CatalogItemEntity.new(id) {
                this.type = org.bscm.models.enums.CatalogItemType.TOUR_PASS
                this.status = CatalogItemStatus.PUBLISHED
                this.author = UserEntity[userId]
            }
        } else {
            CatalogItemEntity.new {
                this.type = org.bscm.models.enums.CatalogItemType.TOUR_PASS
                this.status = CatalogItemStatus.PUBLISHED
                this.author = UserEntity[userId]
            }
        }

        val tourPass = TourPassEntity.new(catalogItem.id.value) {
            this.name = name
            this.description = description
            this.artist = artist
            this.coverId = coverUrl
        }

        chartIds?.forEach { cid ->
            TourPassChartTable.insertIgnore {
                it[TourPassChartTable.tourPassId] = EntityID(tourPass.id.value, TourPassTable)
                it[TourPassChartTable.chartId] = EntityID(cid, ChartTable)
            }
        }

        tourPassEntityToTourPass(tourPass)
    }

    override suspend fun updateTourPass(
        id: String,
        userId: UUID,
        name: String?,
        description: String?,
        artist: String?,
        coverUrl: String?,
        chartIds: List<String>?,
    ): TourPass = newSuspendedTransaction {
        val entity = TourPassEntity.findByIdAndUpdate(id) { entity ->
            name?.let { entity.name = it }
            description?.let { entity.description = it }
            artist?.let { entity.artist = it }
            coverUrl?.let { entity.coverId = it }
        } ?: throw IllegalArgumentException("TourPass $id not found")

        chartIds?.let { newChartIds ->
            TourPassChartTable.deleteWhere { TourPassChartTable.tourPassId eq EntityID(id, TourPassTable) }
            newChartIds.forEach { cid ->
                TourPassChartTable.insertIgnore {
                    it[TourPassChartTable.tourPassId] = EntityID(id, TourPassTable)
                    it[TourPassChartTable.chartId] = EntityID(cid, ChartTable)
                }
            }
        }

        tourPassEntityToTourPass(entity)
    }

    override suspend fun deleteTourPass(id: String, userId: UUID): Boolean = newSuspendedTransaction {
        CatalogItemEntity.findById(id)?.delete() ?: return@newSuspendedTransaction false
        true
    }

    override suspend fun setTourPassCharts(id: String, userId: UUID, chartIds: List<String>): TourPass = newSuspendedTransaction {
        TourPassEntity.findById(id) ?: throw IllegalArgumentException("TourPass $id not found")

        TourPassChartTable.deleteWhere { TourPassChartTable.tourPassId eq EntityID(id, TourPassTable) }
        chartIds.forEach { cid ->
            TourPassChartTable.insertIgnore {
                it[TourPassChartTable.tourPassId] = EntityID(id, TourPassTable)
                it[TourPassChartTable.chartId] = EntityID(cid, ChartTable)
            }
        }

        tourPassEntityToTourPass(TourPassEntity[id])
    }

    override suspend fun addChartToTourPass(tourPassId: String, chartId: String): Boolean = newSuspendedTransaction {
        TourPassChartTable.insertIgnore {
            it[TourPassChartTable.tourPassId] = EntityID(tourPassId, TourPassTable)
            it[TourPassChartTable.chartId] = EntityID(chartId, ChartTable)
        }.insertedCount > 0
    }

    override suspend fun removeChartFromTourPass(tourPassId: String, chartId: String): Boolean = newSuspendedTransaction {
        TourPassChartTable.deleteWhere {
            (TourPassChartTable.tourPassId eq EntityID(tourPassId, TourPassTable)) and
                (TourPassChartTable.chartId eq EntityID(chartId, ChartTable))
        } > 0
    }
}
