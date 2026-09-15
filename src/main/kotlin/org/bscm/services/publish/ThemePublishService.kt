package org.bscm.services.publish

import io.ktor.http.*
import io.ktor.server.plugins.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.bscm.models.Theme
import org.bscm.models.User
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.dto.version.VersionBundleData
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.IUserRepository
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
    private val userRepository: IUserRepository,
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

        // Hoisted to locals so the concurrent uploads below need no casts.
        val coverArtBytes: ByteArray = assets.coverArtBytes
            ?: throw BadRequestException("Cover art file is required")
        val displayArtBytes: ByteArray = assets.displayArtBytes
            ?: throw BadRequestException("Display art file is required")

        // The two artwork uploads are independent — run them concurrently.
        // Fail-fast is preserved: the first failure cancels its sibling and propagates.
        coroutineScope {
            awaitAll(
                async {
                    val avifCoverBytes = MediaConverter.convertToAvif(coverArtBytes) ?: coverArtBytes
                    storageService.uploadThemeCover(catalogId, avifCoverBytes)
                },
                async {
                    val avifDisplayBytes = MediaConverter.convertToAvif(displayArtBytes) ?: displayArtBytes
                    storageService.uploadThemeDisplay(catalogId, avifDisplayBytes)
                },
            )
        }

        val coverUrl = storageService.themeCoverUrl(catalogId)
        val displayArtUrl = storageService.themeDisplayUrl(catalogId)

        val metadataContributors = userRepository.resolveMetadataContributors(
            authorId = uploader.id,
            authorUsername = uploader.username,
            authorAvatarUrl = uploader.avatarUrl,
            contributors = request.contributors,
        )
        val themeMetadata = DecodingUtils.ThemeMetadata(
            catalogId = catalogId,
            name = request.name,
            replaces = request.replaces,
            contributors = metadataContributors,
        )
        val enrichedBundleBytes = DecodingUtils.injectMetadata(assets.bundleBytes, themeMetadata)

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
                coverArtUrl = coverUrl,
                displayArtUrl = displayArtUrl,
            ),
            themeBundle = enrichedBundleBytes,
        )

        val bundleAttachment = discordResponse.attachments.firstOrNull { it.filename.endsWith(".zip") }
            ?: throw IllegalStateException("Discord response missing bundle attachment after theme upload")

        emitEvent(PublishStep.CREATING_CHART, "Creating theme in database")

        val (theme, version) = try {
            suspendTransaction {
                val createdTheme = themeRepository.createTheme(
                    userId = uploader.id,
                    name = request.name,
                    replaces = request.replaces,
                    originalArtwork = request.originalArtwork,
                    id = catalogId,
                    contributors = request.contributors,
                )

                themeRepository.updateDiscordCoordinates(
                    catalogId,
                    discordResponse.channelId,
                    discordResponse.id,
                )

                emitEvent(PublishStep.FINALIZING_VERSION)

                val v = versionRepository.addVersion(
                    catalogItemId = catalogId,
                    version = VersionBundleData(
                        id = bundleAttachment.id.toULong(),
                        fileSizeBytes = assets.bundleBytes.size.toLong(),
                        changelog = "",
                    ),
                    bundleHash = bundleHash,
                )

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
            versions = listOf(version),
            versionsCount = 1,
        )
    }

    suspend fun deleteAndCleanup(id: String, userId: UUID): Boolean {
        val theme = themeRepository.getThemeById(id, userId) ?: return false
        if (theme.authorId != userId) {
            throw SecurityException("You are not the author of this theme")
        }
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
            originalArtwork = request.originalArtwork
        )
    }
}
