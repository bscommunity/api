package org.bscm.repository.implementation

import org.bscm.models.UserInteraction
import org.bscm.models.dao.*
import org.bscm.models.enums.ContentType
import org.bscm.models.tables.InteractionTable
import org.bscm.repository.UserInteractionRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class UserInteractionRepositoryImpl : UserInteractionRepository {

    override suspend fun likeContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean =
        newSuspendedTransaction {
            val existing = findInteraction(userId, contentType, contentId)

            if (existing != null) {
                existing.likedAt = LocalDateTime.now()
                true
            } else {
                createInteraction(userId, contentType, contentId, likedAt = LocalDateTime.now())
                true
            }
        }

    override suspend fun unlikeContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean =
        newSuspendedTransaction {
            val existing = findInteraction(userId, contentType, contentId) ?: return@newSuspendedTransaction false

            existing.likedAt = null

            // If no interactions remain, delete the record
            if (existing.likedAt == null && existing.favoritedAt == null) {
                existing.delete()
            }
            true
        }

    override suspend fun favoriteContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean =
        newSuspendedTransaction {
            val existing = findInteraction(userId, contentType, contentId)

            if (existing != null) {
                existing.favoritedAt = LocalDateTime.now()
                true
            } else {
                createInteraction(userId, contentType, contentId, favoritedAt = LocalDateTime.now())
                true
            }
        }

    override suspend fun unfavoriteContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean =
        newSuspendedTransaction {
            val existing = findInteraction(userId, contentType, contentId) ?: return@newSuspendedTransaction false

            existing.favoritedAt = null

            // If no interactions remain, delete the record
            if (existing.likedAt == null && existing.favoritedAt == null) {
                existing.delete()
            }
            true
        }

    override suspend fun getUserInteraction(
        userId: UUID, contentType: ContentType, contentId: ULong
    ): UserInteraction? = newSuspendedTransaction {
        val entity = findInteraction(userId, contentType, contentId) ?: return@newSuspendedTransaction null

        UserInteraction(
            userId = userId,
            contentType = contentType,
            contentId = contentId,
            likedAt = entity.likedAt,
            favoritedAt = entity.favoritedAt
        )
    }

    override suspend fun getUserLikedContent(
        userId: UUID, contentType: ContentType, limit: Int?, offset: Int?
    ): List<ULong> = newSuspendedTransaction {
        val query = InteractionTable.selectAll().where {
            (InteractionTable.userId eq userId) and (InteractionTable.likedAt.isNotNull()) and getContentTypeFilter(
                contentType
            )
        }.orderBy(InteractionTable.likedAt, SortOrder.DESC)

        if (limit != null) {
            query.limit(limit).offset(offset?.toLong() ?: 0)
        }

        query.map { getContentIdFromRow(it, contentType) }
    }

    override suspend fun getUserFavoritedContent(
        userId: UUID, contentType: ContentType, limit: Int?, offset: Int?
    ): List<ULong> = newSuspendedTransaction {
        val query = InteractionTable.selectAll().where {
            (InteractionTable.userId eq userId) and (InteractionTable.favoritedAt.isNotNull()) and getContentTypeFilter(
                contentType
            )
        }.orderBy(InteractionTable.favoritedAt, SortOrder.DESC)

        if (limit != null) {
            query.limit(limit).offset(offset?.toLong() ?: 0)
        }

        query.map { getContentIdFromRow(it, contentType) }
    }

    override suspend fun getContentInteractionStats(contentType: ContentType, contentId: ULong): Map<String, Int> =
        newSuspendedTransaction {
            val filter = getContentTypeFilter(contentType) and getContentIdFilter(contentType, contentId)

            val likesCount = InteractionTable.selectAll().where {
                filter and InteractionTable.likedAt.isNotNull()
            }.count().toInt()

            val favoritesCount = InteractionTable.selectAll().where {
                filter and InteractionTable.favoritedAt.isNotNull()
            }.count().toInt()

            mapOf(
                "likes" to likesCount, "favorites" to favoritesCount
            )
        }

    private fun findInteraction(userId: UUID, contentType: ContentType, contentId: ULong): InteractionEntity? {
        val filter = (InteractionTable.userId eq userId) and getContentTypeFilter(contentType) and getContentIdFilter(
            contentType, contentId
        )

        return InteractionEntity.find { filter }.firstOrNull()
    }

    private fun createInteraction(
        userId: UUID,
        contentType: ContentType,
        contentId: ULong,
        likedAt: LocalDateTime? = null,
        favoritedAt: LocalDateTime? = null
    ): InteractionEntity {
        return InteractionEntity.new {
            this.user = UserEntity[userId]
            when (contentType) {
                ContentType.CHART -> this.chart = ChartEntity[contentId]
                ContentType.TOUR_PASS -> this.tourPass = TourPassEntity[contentId]
                ContentType.THEME -> this.theme = ThemeEntity[contentId]
            }
            this.likedAt = likedAt
            this.favoritedAt = favoritedAt
        }
    }

    private fun getContentTypeFilter(contentType: ContentType): Op<Boolean> {
        return when (contentType) {
            ContentType.CHART -> InteractionTable.chartId.isNotNull()
            ContentType.TOUR_PASS -> InteractionTable.tourPassId.isNotNull()
            ContentType.THEME -> InteractionTable.themeId.isNotNull()
        }
    }

    private fun getContentIdFilter(contentType: ContentType, contentId: ULong): Op<Boolean> {
        return when (contentType) {
            ContentType.CHART -> InteractionTable.chartId eq contentId
            ContentType.TOUR_PASS -> InteractionTable.tourPassId eq contentId
            ContentType.THEME -> InteractionTable.themeId eq contentId
        }
    }

    private fun getContentIdFromRow(row: ResultRow, contentType: ContentType): ULong {
        return when (contentType) {
            ContentType.CHART -> row[InteractionTable.chartId]!!.value
            ContentType.TOUR_PASS -> row[InteractionTable.tourPassId]!!.value
            ContentType.THEME -> row[InteractionTable.themeId]!!.value
        }
    }
}
