package org.bscm.services

import io.ktor.server.plugins.*
import io.ktor.util.logging.*
import org.bscm.models.Chart
import org.bscm.models.StreamingRef
import org.bscm.models.User
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.dto.version.SimplifiedVersion
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IChartRepository
import org.bscm.plugins.ConflictException
import org.bscm.protobuf.ChartParser
import org.bscm.services.track.TrackInfoService
import org.bscm.storage.StorageService
import org.bscm.utils.DecodingUtils
import org.bscm.utils.MediaConverter
import org.bscm.utils.NanoIdUtils
import java.security.MessageDigest
import java.util.*

private val log = KtorSimpleLogger("ChartPublishService")

/** Centralized pipeline for publishing a chart (Discord upload + DB persist). */
class ChartPublishService(
    private val chartRepository: IChartRepository,
    private val uploadService: UploadService,
    private val storageService: StorageService,
    private val trackInfoService: TrackInfoService,
    private val audioPreviewService: AudioPreviewService,
    private val activityRepository: IActivityRepository,
    private val publishEventService: PublishEventService
) {
    enum class CoverSource { BUNDLE, MEDIA_INFO }

    companion object {
        private val COVER_SOURCE = CoverSource.BUNDLE
    }

    data class Overrides(
        val track: String? = null,
        val artist: String? = null,
        val isExplicit: Boolean? = null,
        val previewUrl: String? = null,
        val coverUrl: String? = null,
        val bpm: Int? = null,
        val isDeluxe: Boolean? = null,
        val trackUrls: List<StreamingRef>? = null,
        val album: String? = null,
        val genres: List<Genre>? = null,
    )

    data class Result(
        val chart: Chart,
        val initialVersion: SimplifiedVersion,
        val discordMessageId: String,
        val versionAttachmentId: String,
    )

    /**
     * Deletes a chart and removes its creation log in a single application-level flow.
     * Returns the Discord message ID for cleanup, or null if no Discord message exists.
     * @throws NotFoundException if the chart does not exist.
     * @throws ForbiddenException if the user is not the chart author.
     */
    suspend fun deleteChartAndCleanup(chartId: String, requestingUserId: UUID): String? {
        val chart = chartRepository.getChartById(chartId)
            ?: throw NotFoundException("Chart not found")
        if (chart.authorId != requestingUserId) {
            throw SecurityException("You are not the author of this chart")
        }

        storageService.deleteTrackCover(chart.track.id)
        storageService.deleteTrackPreview(chart.track.id)

        chartRepository.deleteChart(chartId)
        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_CHART,
            targetId = chartId
        )
        return chart.discordMessageId
    }


    suspend fun publish(user: User, bundleBytes: ByteArray, overrides: Overrides = Overrides(), publishSessionId: String? = null): Result {
        fun emitEvent(step: PublishStep) {
            if (publishSessionId != null) {
                publishEventService.emit(publishSessionId, step)
            }
        }

        emitEvent(PublishStep.EXTRACTING_BUNDLE)

        val bundleHash = MessageDigest.getInstance("SHA-256")
            .digest(bundleBytes)
            .joinToString("") { "%02x".format(it) }

        chartRepository.findChartByBundleHash(bundleHash)?.let { existing ->
            throw ConflictException(
                "A chart with this bundle already exists (id: ${existing.id})",
            )
        }

        val contentId = NanoIdUtils.generateOptimized(
            10,
            "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            63,
            16
        )

        // 1. Extract info.json metadata
        val bundleInfo = DecodingUtils.extractBundleInfo(bundleBytes)

        // 2. Extract cover image from bundle (will be uploaded to storage after track creation)
        val coverBytes = DecodingUtils.extractCoverImage(bundleBytes)

        emitEvent(PublishStep.PARSING_CHART)

        // 3. Extract chart.bytes and parse
        val chartBytes = DecodingUtils.extractChartFileFromBundle(bundleBytes)
            ?: throw IllegalStateException("Failed to extract chart.bytes from bundle")
        val parsedProto = ChartParser.parse(chartBytes)
        val computedStats = DecodingUtils.computeChartStats(parsedProto, bundleInfo?.bpm)

        val difficultyEnum = when (bundleInfo?.difficulty) {
            4 -> Difficulty.NORMAL
            3 -> Difficulty.HARD
            1 -> Difficulty.EXTREME
            else -> Difficulty.NORMAL
        }

        // 4. Resolve metadata with override precedence
        val trackName = overrides.track ?: bundleInfo?.title ?: "Unknown"
        val artistName = overrides.artist ?: bundleInfo?.artist ?: "Unknown"

        emitEvent(PublishStep.FETCHING_MEDIA_INFO)

        // 5. Track info enrichment
        val mediaInfo = try { trackInfoService.getTrackInfo(trackName, artistName) } catch (e: Exception) {
            log.error("Media info fetch failed: ${e.message}")
            null
        }

        val coverUrl = overrides.coverUrl ?: mediaInfo?.coverUrl ?: ""

        // Track streaming links resolution
        val streamingResult = overrides.trackUrls?.let { TrackInfoService.StreamingLinksResult(it) } ?: run {
            try {
                if (mediaInfo?.link != null) {
                    trackInfoService.getTrackStreamingLinks(mediaInfo.link.url, trackName, artistName, mediaInfo.isrc, mediaInfo.link.platform)
                } else TrackInfoService.StreamingLinksResult(emptyList())
            } catch (_: Exception) {
                TrackInfoService.StreamingLinksResult(listOfNotNull(mediaInfo?.link))
            }
        }

        val streamingLinks = buildList {
            mediaInfo?.link?.let { add(it) }
            addAll(streamingResult.links.filter { it.platform != mediaInfo?.link?.platform })
        }
        val resolvedIsrc = mediaInfo?.isrc ?: streamingResult.isrc

        log.info("Resolved streaming links: $streamingLinks")

        val bpm = overrides.bpm ?: bundleInfo?.bpm ?: 0
        val isDeluxe = overrides.isDeluxe ?: (bundleInfo?.type?.equals("Promode", ignoreCase = true) ?: false)
        val isExplicit = overrides.isExplicit ?: mediaInfo?.isExplicit ?: false

        emitEvent(PublishStep.CREATING_CHART)

        // 6. Create DB entities first (without version)
        val createForDb = CreateChartRequest(
            artist = mediaInfo?.artist ?: artistName,
            track = mediaInfo?.track ?: trackName,
            album = overrides.album ?: mediaInfo?.album,
            trackUrls = streamingLinks,
            coverUrl = coverUrl,
            genres = (overrides.genres ?: mediaInfo?.genres).orEmpty(),
            isExplicit = isExplicit,
            duration = computedStats.duration,
            notesAmount = computedStats.notesAmount,
            effectsAmount = computedStats.effectsAmount,
            bpm = bpm,
            difficulty = difficultyEnum,
            isDeluxe = isDeluxe,
            bundleUrl = "",
            fileSizeBytes = bundleBytes.size.toLong(),
            bundleHash = bundleHash,
            previewUrl = overrides.previewUrl,
            contentId = contentId,
            isrc = resolvedIsrc,
        )

        val createdChart = chartRepository.createChart(user.id, createForDb)

        log.info("Created chart ${createdChart.id}")

        emitEvent(PublishStep.PREPARING_BUNDLE)

        // 7. Inject bscm.json into the bundle (basic display data + cover art)
        val coverCdnUrl = storageService.trackCoverUrl(createdChart.track.id)
        val bscmMetadata = DecodingUtils.BscmMetadata(
            chartId = createdChart.id,
            track = trackName,
            artist = artistName,
            difficulty = difficultyEnum.ordinal,
            isDeluxe = isDeluxe,
            isExplicit = isExplicit,
            bpm = bpm,
            duration = computedStats.duration,
            notes = computedStats.notesAmount,
            effects = computedStats.effectsAmount,
            contributors = listOf(
                DecodingUtils.BscmContributor(
                    username = user.username,
                    avatarUrl = user.avatarUrl,
                    role = "author",
                )
            ),
            cover = coverCdnUrl.ifEmpty { null },
        )
        val enrichedBundleBytes = DecodingUtils.injectBscmMetadata(bundleBytes, bscmMetadata)

        emitEvent(PublishStep.UPLOADING_TO_DISCORD)

        // 10. Upload enriched bundle to Discord (single call)
        val discordResponse = uploadService.uploadChart(createForDb, user, enrichedBundleBytes, coverBytes)
        val bundleAttachment = discordResponse.attachments.firstOrNull { it.filename.endsWith(".zip") }
            ?: throw IllegalStateException("Discord response missing bundle attachment")

        // 9b. Persist Discord message/channel IDs so delete/edit operations use the snowflake
        chartRepository.updateDiscordCoordinates(
            catalogItemId = createdChart.id,
            channelId = discordResponse.channelId,
            messageId = discordResponse.id,
        )

        emitEvent(PublishStep.FINALIZING_VERSION)

        // 10. Finalize: add version with Discord bundle URL
        val version = chartRepository.addVersion(
            catalogItemId = createdChart.id,
            version = CreateVersionRequest(
                id = bundleAttachment.id.toULong(),
                track = createForDb.track,
                artist = createForDb.artist,
                duration = createForDb.duration,
                notesAmount = createForDb.notesAmount,
                effectsAmount = createForDb.effectsAmount,
                bpm = createForDb.bpm,
                difficulty = createForDb.difficulty,
                isDeluxe = createForDb.isDeluxe,
                isExplicit = createForDb.isExplicit,
                bundleUrl = bundleAttachment.url,
                previewUrl = createForDb.previewUrl,
                fileSizeBytes = createForDb.fileSizeBytes,
            )
        )

        val updatedChart = createdChart.copy(
            latestVersion = version,
            versionsCount = createdChart.versionsCount + 1,
        )

        emitEvent(PublishStep.UPLOADING_COVER)

        // 11. Convert and upload cover image to storage
        if (coverBytes != null) {
            try {
                val avifBytes = MediaConverter.convertToAvif(coverBytes) ?: coverBytes
                storageService.uploadTrackCover(createdChart.track.id, avifBytes)
            } catch (e: Exception) {
                log.warn("Failed to upload track cover to storage: ${e.message}")
            }
        }

        emitEvent(PublishStep.GENERATING_PREVIEW)

        // 12. Download, convert, and upload audio preview to storage
        if (mediaInfo != null) {
            audioPreviewService.publish(createdChart.track.id, mediaInfo)
        }

        val result = Result(
            chart = updatedChart,
            initialVersion = SimplifiedVersion(
                difficulty = createForDb.difficulty,
                duration = createForDb.duration,
                notesAmount = createForDb.notesAmount,
                effectsAmount = createForDb.effectsAmount,
                isDeluxe = createForDb.isDeluxe,
                isExplicit = createForDb.isExplicit,
            ),
            discordMessageId = discordResponse.id,
            versionAttachmentId = bundleAttachment.id,
        )

        emitEvent(PublishStep.LOGGING_ACTIVITY)

        activityRepository.logActivity(
            userId = user.id,
            type = ActivityType.CREATED_CHART,
            targetId = createdChart.id
        )

        emitEvent(PublishStep.COMPLETED)

        return result
    }
}
