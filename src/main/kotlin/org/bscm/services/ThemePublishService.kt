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
import org.bscm.utils.NanoIdUtils
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
        val skinBytes: ByteArray?,
        val skinContentType: ContentType?,
    )

    suspend fun createAndPublish(
        uploader: User,
        request: CreateThemeRequest,
        assets: Assets,
    ): Theme {
        if (assets.coverArtBytes == null && request.coverUrl.isNullOrBlank()) {
            throw BadRequestException("coverUrl or coverArt file is required")
        }
        if (assets.skinBytes == null && request.skinUrl.isNullOrBlank()) {
            throw BadRequestException("skinUrl or skin file is required")
        }

        val contentId = NanoIdUtils.generateContentId()

        if (assets.coverArtBytes != null) {
            storageService.uploadThemeCover(contentId, assets.coverArtBytes)
        }
        if (assets.skinBytes != null) {
            storageService.uploadThemeSkin(contentId, assets.skinBytes)
        }

        val coverUrl = storageService.themeCoverUrl(contentId)
        val skinUrl = storageService.themeSkinUrl(contentId)

        val discordResponse = uploadService.uploadTheme(
            UploadService.ThemePublishData(
                title = request.name,
                description = request.description,
                context = UploadService.PublishContext(
                    contentId = contentId,
                    submittedBy = UploadService.SubmittedBy.fromUser(uploader)
                ),
                replaces = request.replaces,
                trailerUrl = request.previewUrl,
                coverArtUrl = coverUrl,
                skinUrl = skinUrl,
                coverBytes = assets.coverArtBytes,
                skinBytes = assets.skinBytes,
            )
        )

        val theme = themeRepository.createTheme(
            userId = uploader.id,
            name = request.name,
            replaces = request.replaces,
            previewUrl = request.previewUrl,
            id = contentId,
        )

        themeRepository.updateDiscordCoordinates(
            contentId,
            discordResponse.channelId,
            discordResponse.id,
        )

        activityRepository.logActivity(
            userId = uploader.id,
            type = ActivityType.CREATED_THEME,
            targetId = theme.id
        )

        return theme
    }

    suspend fun deleteAndCleanup(id: String, userId: UUID): Boolean {
        val theme = themeRepository.getThemeById(id, userId) ?: return false
        val deleted = themeRepository.deleteTheme(id, userId)
        if (!deleted) return false

        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_THEME,
            targetId = theme.id
        )

        theme.discordMessageId?.let { messageId ->
            runCatching { uploadService.deleteMessage(messageId) }
        }
        return true
    }

    suspend fun updateAndPublish(
        id: String,
        userId: UUID,
        request: UpdateThemeRequest,
        assets: Assets,
    ): Theme {
        if (assets.coverArtBytes != null) {
            storageService.uploadThemeCover(id, assets.coverArtBytes)
        }
        if (assets.skinBytes != null) {
            storageService.uploadThemeSkin(id, assets.skinBytes)
        }

        return themeRepository.updateTheme(
            id = id,
            userId = userId,
            name = request.name,
            replaces = request.replaces,
            previewUrl = request.previewUrl
        )
    }
}
