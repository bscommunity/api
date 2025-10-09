package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.Chart
import org.bscm.models.TourPass
import org.bscm.models.dao.ContentEntity
import org.bscm.models.dao.TourPassEntity
import org.bscm.models.enums.ContentType
import org.bscm.models.tables.TourPassChartTable
import org.bscm.models.tables.TourPassTable
import org.bscm.repository.ChartRepository
import org.bscm.repository.TourPassRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class TourPassRepositoryImpl(
    private val chartRepository: ChartRepository
) : TourPassRepository {

    private fun daoToTourPass(entity: TourPassEntity, charts: List<Chart>): TourPass {
        return TourPass(
            id = entity.id.value.toString(),
            contentId = entity.content.id.value,
            name = entity.name,
            artist = entity.artist,
            coverUrl = entity.coverUrl,
            charts = charts,
            isPublic = entity.isPublic,
            isFeatured = entity.isFeatured,
            downloadsSum = entity.downloadsSum,
            latestPublishedAt = entity.latestPublishedAt ?: LocalDateTime.now()
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

        // Only return public tour passes if userId is null
        if (userId == null) {
            query.andWhere { TourPassTable.isPublic eq true }
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
        query.orderBy(TourPassTable.latestPublishedAt to SortOrder.DESC)

        if (limit != null) {
            query.limit(limit).offset(offset?.toLong() ?: 0)
        }

        val tourPassEntities = TourPassEntity.wrapRows(query).toList()

        tourPassEntities.map { tourPassEntity ->
            val charts = getChartsForTourPass(tourPassEntity.id.value, userId)
            daoToTourPass(tourPassEntity, charts)
        }
    }

    override suspend fun getTourPassById(id: ULong): TourPass? = newSuspendedTransaction {
        val entity = TourPassEntity.findById(id) ?: return@newSuspendedTransaction null
        val charts = getChartsForTourPass(id, null)
        daoToTourPass(entity, charts)
    }

    override suspend fun getAppTourPassById(contentId: String): TourPass? = newSuspendedTransaction {
        val entity = TourPassEntity.find { TourPassTable.contentId eq contentId }.firstOrNull()
            ?: return@newSuspendedTransaction null
        val charts = getChartsForTourPass(entity.id.value, null)
        daoToTourPass(entity, charts)
    }

    override suspend fun createTourPass(
        userId: UUID,
        name: String,
        artist: String?,
        coverUrl: String
    ): TourPass = newSuspendedTransaction {
        // Create new content entry
        val content = ContentEntity.new {
            this.type = ContentType.TOUR_PASS
        }

        val entity = TourPassEntity.new {
            this.content = content
            this.name = name
            this.artist = artist
            this.coverUrl = coverUrl
            this.isPublic = true
            this.isFeatured = false
            this.downloadsSum = 0
            this.latestPublishedAt = LocalDateTime.now()
        }
        daoToTourPass(entity, emptyList())
    }

    override suspend fun updateTourPass(
        id: ULong,
        name: String?,
        artist: String?,
        coverUrl: String?
    ): TourPass = newSuspendedTransaction {
        val entity = TourPassEntity.findById(id) ?: throw NotFoundException("TourPass not found")

        name?.let { entity.name = it }
        artist?.let { entity.artist = it }
        coverUrl?.let { entity.coverUrl = it }

        val charts = getChartsForTourPass(id, null)
        daoToTourPass(entity, charts)
    }

    override suspend fun deleteTourPass(id: ULong): Boolean = newSuspendedTransaction {
        val entity = TourPassEntity.findById(id) ?: return@newSuspendedTransaction false
        entity.delete()
        true
    }

    private suspend fun getChartsForTourPass(tourPassId: ULong, userId: UUID?): List<Chart> {
        val chartIds = TourPassChartTable
            .selectAll()
            .where { TourPassChartTable.tourPassId eq tourPassId }
            .map { it[TourPassChartTable.chartId].value }

        if (chartIds.isEmpty()) return emptyList()

        // Use the real ChartRepository to get full chart details with versions, contributors, etc.
        return chartRepository.getCharts(
            userId = userId,
            chartIds = chartIds,
            search = null,
            sortBy = null,
            difficulties = null,
            genres = null,
            limit = null,
            offset = null
        )
    }

    override suspend fun addChartToTourPass(tourPassId: ULong, chartId: ULong): Boolean = newSuspendedTransaction {
        // Check if tour pass exists
        val tourPass = TourPassEntity.findById(tourPassId) ?: return@newSuspendedTransaction false

        // Check if chart is already in tour pass
        val existing = TourPassChartTable.selectAll()
            .where { (TourPassChartTable.tourPassId eq tourPassId) and (TourPassChartTable.chartId eq chartId) }
            .firstOrNull()

        if (existing != null) return@newSuspendedTransaction false

        // Add chart to tour pass
        TourPassChartTable.insert {
            it[TourPassChartTable.tourPassId] = tourPassId
            it[TourPassChartTable.chartId] = chartId
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
            updateTourPassStats(tourPassId)
            true
        } else {
            false
        }
    }

    private suspend fun updateTourPassStats(tourPassId: ULong) = newSuspendedTransaction {
        val charts = getChartsForTourPass(tourPassId, null)

        TourPassEntity.findByIdAndUpdate(tourPassId) { entity ->
            entity.downloadsSum = charts.sumOf { it.downloadsSum }
            entity.latestPublishedAt = charts.maxOfOrNull { it.latestPublishedAt } ?: LocalDateTime.now()
        }
    }
}
