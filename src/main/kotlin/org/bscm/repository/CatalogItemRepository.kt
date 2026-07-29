package org.bscm.repository

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.Visibility
import org.bscm.utils.UserStatsUtils
import java.util.*
import kotlin.time.Clock

class CatalogItemRepository {
    fun getById(id: String): CatalogItemEntity? =
        CatalogItemEntity.findById(id)

    fun create(
        type: CatalogItemType,
        authorId: UUID,
        previewVideoId: String? = null,
        contentId: String? = null,
    ): CatalogItemEntity {
        val id =
            if (!contentId.isNullOrBlank() && CatalogItemEntity.findById(contentId) == null)
                contentId
            else
                null

        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        return when {
            id != null -> CatalogItemEntity.new(id) {
                this.type = type
                this.status = CatalogItemStatus.DRAFT
                this.previewVideoId = previewVideoId
                this.author = UserEntity[authorId]
                this.updatedAt = now
            }

            else -> CatalogItemEntity.new {
                this.type = type
                this.status = CatalogItemStatus.DRAFT
                this.previewVideoId = previewVideoId
                this.author = UserEntity[authorId]
                this.updatedAt = now
            }
        }
    }

    fun updateVisibility(
        catalogItemId: String,
        visibility: Visibility,
    ) {
        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.visibility = visibility
            it.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }
    }

    fun updateFeatured(
        catalogItemId: String,
        isFeatured: Boolean,
    ) {
        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.isFeatured = isFeatured
            it.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }
    }

    fun updateDiscordCoordinates(
        catalogItemId: String,
        channelId: String,
        messageId: String,
    ) {
        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.discordChannelId = channelId
            it.discordMessageId = messageId
        }
    }

    fun incrementDownloads(catalogItemId: String) {
        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.downloadsSum += 1
        }
    }

    fun fetchUserStats(
        userId: UUID?,
        contentIds: List<String>,
    ): Map<String, Pair<LocalDateTime?, LocalDateTime?>> =
        UserStatsUtils.fetchUserStats(userId, contentIds)
}
