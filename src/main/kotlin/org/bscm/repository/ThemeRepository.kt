package org.bscm.repository

import org.bscm.models.Theme
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.ThemeEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.storage.StorageService
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class ThemeRepository(
    private val catalogItemRepository: CatalogItemRepository,
    private val storageService: StorageService,
) : IThemeRepository {

    private fun themeEntityToTheme(entity: ThemeEntity): Theme {
        val catalogItem = CatalogItemEntity[entity.id.value]
        val id = entity.id.value
        return Theme(
            name = entity.name,
            replaces = entity.replaces,
            skinUrl = storageService.themeSkinUrl(id),
            previewUrl = entity.previewUrl,
            coverUrl = storageService.themeCoverUrl(id),
            contributors = emptyList(),
            createdAt = catalogItem.createdAt,
            publishedAt = catalogItem.publishedAt,
            updatedAt = catalogItem.updatedAt,
            likedAt = null,
            bookmarkedAt = null,
            id = id,
            type = CatalogItemType.THEME,
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

    override suspend fun getThemes(
        userId: UUID?,
        contentIds: List<String>?,
        search: String?,
        limit: Int?,
        offset: Int?,
    ): List<Theme> = suspendTransaction {
        val pageSize = limit ?: 20
        val pageOffset = offset ?: 0

        val result: List<ThemeEntity> = if (contentIds != null && contentIds.isNotEmpty()) {
            ThemeEntity.all().filter { it.id.value in contentIds }
        } else {
            ThemeEntity.all().limit(pageSize).offset(pageOffset.toLong()).toList()
        }

        val paged = if (contentIds != null && contentIds.isNotEmpty()) {
            result.drop(pageOffset).take(pageSize)
        } else {
            result
        }
        paged.map { themeEntityToTheme(it) }
    }

    override suspend fun getThemeById(id: String, userId: UUID?): Theme? = suspendTransaction {
        ThemeEntity.findById(id)?.let { themeEntityToTheme(it) }
    }

    override suspend fun createTheme(
        userId: UUID,
        name: String,
        replaces: String,
        previewUrl: String?,
        id: String?,
    ): Theme = suspendTransaction {
        val catalogItem = if (id != null) {
            CatalogItemEntity.new(id) {
                this.type = CatalogItemType.THEME
                this.status = CatalogItemStatus.PUBLISHED
                this.author = UserEntity[userId]
            }
        } else {
            CatalogItemEntity.new {
                this.type = CatalogItemType.THEME
                this.status = CatalogItemStatus.PUBLISHED
                this.author = UserEntity[userId]
            }
        }

        val theme = ThemeEntity.new(catalogItem.id.value) {
            this.name = name
            this.replaces = replaces
            this.previewUrl = previewUrl
        }

        themeEntityToTheme(theme)
    }

    override suspend fun updateTheme(
        id: String,
        userId: UUID,
        name: String?,
        replaces: String?,
        previewUrl: String?,
    ): Theme = suspendTransaction {
        val entity = ThemeEntity.findByIdAndUpdate(id) { entity ->
            name?.let { entity.name = it }
            replaces?.let { entity.replaces = it }
            previewUrl?.let { entity.previewUrl = it }
        } ?: throw IllegalArgumentException("Theme $id not found")

        themeEntityToTheme(entity)
    }

    override suspend fun deleteTheme(id: String, userId: UUID): Boolean = suspendTransaction {
        CatalogItemEntity.findById(id)?.delete() ?: return@suspendTransaction false
        true
    }

    override suspend fun updateDiscordCoordinates(catalogItemId: String, channelId: String, messageId: String) {
        catalogItemRepository.updateDiscordCoordinates(catalogItemId, channelId, messageId)
    }
}
