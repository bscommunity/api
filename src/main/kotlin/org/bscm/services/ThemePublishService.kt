package org.bscm.services

import io.ktor.server.plugins.*
import org.bscm.models.Theme
import org.bscm.models.User
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IThemeRepository

class ThemePublishService(
    private val themeRepository: IThemeRepository,
    private val uploadService: UploadService,
    private val activityRepository: IActivityRepository,
) {
    data class Assets(
        val coverArt: UploadService.UploadImage?,
        val displayArt: UploadService.UploadImage?,
    )

    suspend fun createAndPublish(
        uploader: User,
        request: CreateThemeRequest,
        assets: Assets,
    ): Theme {
        if (assets.coverArt == null && request.coverUrl.isNullOrBlank()) {
            throw BadRequestException("coverUrl or coverArt file is required")
        }
        if (assets.displayArt == null && request.displayArtUrl.isNullOrBlank()) {
            throw BadRequestException("displayArtUrl or displayArt file is required")
        }

        val discordResponse = uploadService.uploadTheme(
            UploadService.ThemePublishData(
                title = request.name,
                description = request.description,
                uploader = uploader,
                replaces = request.replaces,
                trailerUrl = request.previewUrl,
                coverArtUrl = request.coverUrl,
                coverArt = assets.coverArt,
                displayArtUrl = request.displayArtUrl,
                displayArt = assets.displayArt,
                trackUrls = emptyList(),
            )
        )

        val resolvedDisplayArtUrl = discordResponse.attachments
            .firstOrNull { it.filename.contains("display-art", ignoreCase = true) }
            ?.url
            ?: discordResponse.embeds.firstOrNull()?.image?.url
            ?: request.displayArtUrl
            ?: throw IllegalStateException("Unable to resolve displayArt URL from Discord response")

        val resolvedCoverUrl = discordResponse.attachments
            .firstOrNull { it.filename.contains("cover-art", ignoreCase = true) }
            ?.url
            ?: discordResponse.embeds.firstOrNull()?.thumbnail?.url
            ?: request.coverUrl
            ?: throw IllegalStateException("Unable to resolve cover URL from Discord response")

        val theme = themeRepository.createTheme(
            userId = uploader.id,
            name = request.name,
            replaces = request.replaces,
            coverUrl = resolvedCoverUrl,
            displayArtUrl = resolvedDisplayArtUrl,
            previewUrl = request.previewUrl,
            id = discordResponse.id.toULong(),
        )

        activityRepository.logActivity(
            userId = uploader.id,
            type = ActivityType.CREATED_THEME,
            targetId = theme.contentId
        )

        return theme
    }

    suspend fun deleteAndCleanup(id: ULong, userId: java.util.UUID): Boolean {
        val contentId = themeRepository.getThemeById(id, userId)?.contentId ?: return false
        val deleted = themeRepository.deleteTheme(id, userId)
        if (!deleted) return false

        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_THEME,
            targetId = contentId
        )

        // Best effort cleanup - DB state is source of truth.
        runCatching { uploadService.deleteMessage(id.toString()) }
        return true
    }

    suspend fun updateAndPublish(
        id: ULong,
        userId: java.util.UUID,
        request: UpdateThemeRequest,
        assets: Assets,
    ): Theme {
        val resolvedCoverUrl = assets.coverArt?.let {
            uploadService.uploadCoverImage(
                coverBytes = it.bytes,
                filename = it.filename,
                contentType = it.contentType,
                context = "theme-update:$id"
            )
        } ?: request.coverUrl

        val resolvedDisplayArtUrl = assets.displayArt?.let {
            uploadService.uploadCoverImage(
                coverBytes = it.bytes,
                filename = it.filename,
                contentType = it.contentType,
                context = "theme-display-art-update:$id"
            )
        } ?: request.displayArtUrl

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


