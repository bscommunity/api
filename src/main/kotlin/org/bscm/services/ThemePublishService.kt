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
import org.bscm.utils.MediaConverter
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

        val catalogId = NanoIdUtils.generateContentId()

        if (assets.coverArtBytes != null) {
            val avifBytes = MediaConverter.convertToAvif(assets.coverArtBytes) ?: assets.coverArtBytes
            storageService.uploadThemeCover(catalogId, avifBytes)
        }
        if (assets.displayArtBytes != null) {
            val avifBytes = MediaConverter.convertToAvif(assets.displayArtBytes) ?: assets.displayArtBytes
            storageService.uploadThemeDisplay(catalogId, avifBytes)
        }

        val coverUrl = storageService.themeCoverUrl(catalogId)
        val displayArtUrl = storageService.themeDisplayUrl(catalogId)

        val discordResponse = uploadService.uploadTheme(
            UploadService.ThemePublishData(
                title = request.name,
                description = request.description,
                context = UploadService.PublishContext(
                    catalogId = catalogId,
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
            previewUrl = request.previewUrl,
            id = catalogId,
        )

        themeRepository.updateDiscordCoordinates(
            catalogId,
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

        // Best effort cleanup - DB state is source of truth.
        runCatching { storageService.deleteThemeCover(id) }
        runCatching { storageService.deleteThemeDisplay(id) }
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
            val avifBytes = MediaConverter.convertToAvif(assets.coverArtBytes) ?: assets.coverArtBytes
            storageService.uploadThemeCover(id, avifBytes)
        }
        if (assets.displayArtBytes != null) {
            val avifBytes = MediaConverter.convertToAvif(assets.displayArtBytes) ?: assets.displayArtBytes
            storageService.uploadThemeDisplay(id, avifBytes)
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
