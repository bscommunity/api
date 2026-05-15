package org.bscm.repository

import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dao.VersionEntity
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.utils.UserStatsUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class CatalogItemRepository {
    suspend fun getById(id: String): CatalogItemEntity? = newSuspendedTransaction {
        CatalogItemEntity.findById(id)
    }

    suspend fun create(
        type: CatalogItemType,
        authorId: UUID,
        previewVideoId: String? = null,
        contentId: String? = null,
    ): CatalogItemEntity = newSuspendedTransaction {
        if (contentId.isNullOrBlank()) {
            CatalogItemEntity.new {
                this.type = type
                this.status = CatalogItemStatus.DRAFT
                this.previewVideoId = previewVideoId
                this.author = UserEntity[authorId]
            }
        } else {
            CatalogItemEntity.new(contentId) {
                this.type = type
                this.status = CatalogItemStatus.DRAFT
                this.previewVideoId = previewVideoId
                this.author = UserEntity[authorId]
            }
        }
    }

    suspend fun updateLatestVersion(
        catalogItemId: String,
        version: VersionEntity,
    ): Unit = newSuspendedTransaction {
        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.latestVersion = version
        }
    }

    suspend fun updateVisibility(catalogItemId: String, isPublic: Boolean): Unit = newSuspendedTransaction {
        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.isPublic = isPublic
        }
    }

    suspend fun updateFeatured(catalogItemId: String, isFeatured: Boolean): Unit = newSuspendedTransaction {
        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.isFeatured = isFeatured
        }
    }

    suspend fun incrementDownloads(catalogItemId: String): Unit = newSuspendedTransaction {
        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.downloadsSum += 1
        }
    }

    suspend fun fetchUserStats(
        userId: UUID?,
        contentIds: List<String>
    ): Map<String, Pair<LocalDateTime?, LocalDateTime?>> = newSuspendedTransaction {
        UserStatsUtils.fetchUserStats(userId, contentIds)
    }
}

