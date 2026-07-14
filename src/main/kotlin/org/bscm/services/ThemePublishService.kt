package org.bscm.services

import io.ktor.http.*
import io.ktor.server.plugins.*
import org.bscm.models.Theme
import org.bscm.models.User
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.storage.StorageService
import java.util.*

class ThemePublishService(
    private val themeRepository: IThemeRepository,
    private val uploadService: UploadService,
    private val storageService: StorageService,
    private val activityRepository: IActivityRepository,
) {
    data class Assets(
        val coverArtBytes: ByteArray?,
        val coverArtContentType: ContentType?,
        val displayArtBytes: ByteArray?,
        val displayArtContentType: ContentType?,
    )

    suspend fun createAndPublish(
        uploader: User,
        request: CreateThemeRequest,
        assets: Assets,
    ): Theme {
        if (assets.coverArtBytes == null && request.coverUrl.isNullOrBlank()) {
            throw BadRequestException("coverUrl or coverArt file is required")
        }
        if (assets.displayArtBytes == null && request.displayArtUrl.isNullOrBlank()) {
            throw BadRequestException("displayArtUrl or displayArt file is required")
        }

        // Upload assets to storage (using random UUIDs since theme ID isn't known yet)
        val coverUrl = if (assets.coverArtBytes != null) {
            val key = UUID.randomUUID().toString()
            storageService.uploadAssetCover(key, assets.coverArtBytes)
            storageService.assetCoverUrl(key)
        } else {
            request.coverUrl
        }

        val displayArtUrl = if (assets.displayArtBytes != null) {
            val key = UUID.randomUUID().toString()
            storageService.uploadThemeDisplay(key, assets.displayArtBytes)
            storageService.themeDisplayUrl(key)
        } else {
            request.displayArtUrl
        }

        val discordResponse = uploadService.uploadTheme(
            UploadService.ThemePublishData(
                title = request.name,
                description = request.description,
                context = UploadService.PublishContext(
                    submittedBy = UploadService.SubmittedBy.fromUser(uploader)
                ),
                replaces = request.replaces,
                trailerUrl = request.previewUrl,
                coverArtUrl = coverUrl,
                displayArtUrl = displayArtUrl,
            )
        )

        val theme = themeRepository.createTheme(
            userId = uploader.id,
            name = request.name,
            replaces = request.replaces,
            coverUrl = coverUrl!!,
            displayArtUrl = displayArtUrl!!,
            previewUrl = request.previewUrl,
            id = discordResponse.id,
        )

        activityRepository.logActivity(
            userId = uploader.id,
            type = ActivityType.CREATED_THEME,
            targetId = theme.id
        )

        return theme
    }

    suspend fun deleteAndCleanup(id: String, userId: UUID): Boolean {
        val contentId = themeRepository.getThemeById(id, userId)?.id ?: return false
        val deleted = themeRepository.deleteTheme(id, userId)
        if (!deleted) return false

        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_THEME,
            targetId = contentId
        )

        // Best effort cleanup - DB state is source of truth.
        runCatching { uploadService.deleteMessage(id) }
        return true
    }

    suspend fun updateAndPublish(
        id: String,
        userId: UUID,
        request: UpdateThemeRequest,
        assets: Assets,
    ): Theme {
        // Upload assets to storage (using actual theme ID for stable paths)
        val resolvedCoverUrl = if (assets.coverArtBytes != null) {
            storageService.uploadAssetCover(id, assets.coverArtBytes)
            storageService.assetCoverUrl(id)
        } else {
            request.coverUrl
        }

        val resolvedDisplayArtUrl = if (assets.displayArtBytes != null) {
            storageService.uploadThemeDisplay(id, assets.displayArtBytes)
            storageService.themeDisplayUrl(id)
        } else {
            request.displayArtUrl
        }

        return themeRepository.updateTheme(
            id = id,
            userId = userId,
            name = request.name,
            replaces = request.replaces,
            coverUrl = resolvedCoverUrl,
            displayArtUrl = resolvedDisplayArtUrl,
            previewUrl = request.previewUrl
        )
    }
}
