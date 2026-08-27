package org.bscm.services.publish

import io.ktor.http.*
import io.ktor.server.plugins.*
import org.bscm.models.Theme
import org.bscm.models.User
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.dto.version.VersionBundleData
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.plugins.ConflictException
import org.bscm.services.UploadService
import org.bscm.storage.StorageService
import org.bscm.utils.DecodingUtils
import org.bscm.utils.MediaConverter
import org.bscm.utils.NanoIdUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.security.MessageDigest
import java.util.*

class ThemePublishService(
    private val themeRepository: IThemeRepository,
    private val versionRepository: IVersionRepository,
    private val uploadService: UploadService,
    private val storageService: StorageService,
    private val activityRepository: IActivityRepository,
    private val publishEventService: PublishEventService,
) {
    data class Assets(
        val coverArtBytes: ByteArray?,
        val coverArtContentType: ContentType?,
        val displayArtBytes: ByteArray?,
        val displayArtContentType: ContentType?,
        val bundleBytes: ByteArray?,
    )

    suspend fun createAndPublish(
        uploader: User,
        request: CreateThemeRequest,
        assets: Assets,
        publishSessionId: String? = null,
    ): Theme {
        fun emitEvent(step: PublishStep, message: String = step.message) {
            if (publishSessionId != null) {
                publishEventService.emit(publishSessionId, step, message)
            }
        }

        if (assets.coverArtBytes == null) {
            throw BadRequestException("Cover art file is required")
        }
        if (assets.displayArtBytes == null) {
            throw BadRequestException("Display art file is required")
        }
        if (assets.bundleBytes == null) {
            throw BadRequestException("Bundle file is required")
        }

        if (assets.bundleBytes.size > 10 * 1024 * 1024) {
            throw BadRequestException("Bundle file size exceeds 10MB limit")
        }

        emitEvent(PublishStep.EXTRACTING_BUNDLE, "Validating theme bundle")

        val bundleHash = MessageDigest.getInstance("SHA-256")
            .digest(assets.bundleBytes)
            .joinToString("") { "%02x".format(it) }

        themeRepository.findThemeByBundleHash(bundleHash)?.let { existing ->
            throw ConflictException(
                "A theme with this bundle already exists (id: ${existing.id})",
            )
        }

        val catalogId = NanoIdUtils.generateCatalogId()

        emitEvent(PublishStep.UPLOADING_COVER, "Uploading theme artwork")

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

        val bscmMetadata = DecodingUtils.ThemeBscmMetadata(
            themeId = catalogId,
            name = request.name,
            replaces = request.replaces,
            contributors = listOf(
                DecodingUtils.BscmContributor(
                    username = uploader.username,
                    avatarUrl = uploader.avatarUrl,
                    role = "author",
                )
            ),
            cover = coverUrl.ifEmpty { null },
            displayArt = displayArtUrl.ifEmpty { null },
        )
        val enrichedBundleBytes = DecodingUtils.injectBscmMetadata(assets.bundleBytes, bscmMetadata)

        emitEvent(PublishStep.UPLOADING_TO_DISCORD, "Uploading theme to Discord")
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
            ),
            themeBundle = enrichedBundleBytes,
        )

        val bundleAttachment = discordResponse.attachments.firstOrNull { it.filename.endsWith(".zip") }

        emitEvent(PublishStep.CREATING_CHART, "Creating theme in database")

        val (theme, version) = try {
            suspendTransaction {
                val createdTheme = themeRepository.createTheme(
                    userId = uploader.id,
                    name = request.name,
                    replaces = request.replaces,
                    originalArtwork = request.originalArtwork,
                    previewUrl = request.previewUrl,
                    id = catalogId,
                )

                themeRepository.updateDiscordCoordinates(
                    catalogId,
                    discordResponse.channelId,
                    discordResponse.id,
                )

                emitEvent(PublishStep.FINALIZING_VERSION)

                val v = if (bundleAttachment != null) {
                    versionRepository.addVersion(
                        catalogItemId = catalogId,
                        version = VersionBundleData(
                            id = bundleAttachment.id.toULong(),
                            fileSizeBytes = assets.bundleBytes.size.toLong(),
                            changelog = "",
                        ),
                        bundleHash = bundleHash,
                    )
                } else null

                Pair(createdTheme, v)
            }
        } catch (e: Exception) {
            runCatching { uploadService.deleteMessage(discordResponse.id) }
            throw e
        }

        emitEvent(PublishStep.LOGGING_ACTIVITY)

        activityRepository.logActivity(
            userId = uploader.id,
            type = ActivityType.CREATED_THEME,
            targetId = theme.id
        )

        emitEvent(PublishStep.COMPLETED)

        return theme.copy(
            latestVersion = version,
            versionsCount = if (version != null) 1 else 0,
        )
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
            originalArtwork = request.originalArtwork,
            previewUrl = request.previewUrl
        )
    }
}
