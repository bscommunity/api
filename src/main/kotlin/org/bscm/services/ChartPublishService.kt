package org.bscm.services

import io.ktor.util.logging.*
import org.bscm.models.Chart
import org.bscm.models.StreamingRef
import org.bscm.models.User
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.version.SimplifiedVersion
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IChartRepository
import org.bscm.protobuf.ChartParser
import org.bscm.utils.DecodingUtils
import org.bscm.utils.NanoIdUtils
import org.bscm.utils.StreamingPlatformUtils

private val log = KtorSimpleLogger("ChartPublishService")

/** Centralized pipeline for publishing a chart (Discord upload + DB persist). */
class ChartPublishService(
    private val chartRepository: IChartRepository,
    private val uploadService: UploadService,
    private val mediaInfoService: MediaInfoService,
    private val activityRepository: IActivityRepository
) {
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
        val genre: Genre? = null,
    )

    data class Result(
        val chart: Chart,
        val initialVersion: SimplifiedVersion,
        val discordMessageId: String,
        val versionAttachmentId: String,
    )

    /**
     * Deletes a chart and removes its creation log in a single application-level flow.
     * Returns false when the chart does not exist.
     */
    suspend fun deleteChartAndCleanup(chartId: ULong): Boolean {
        val contentId = chartRepository.deleteChartAndGetContentId(chartId) ?: return false
        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_CHART,
            targetId = contentId
        )
        return true
    }


    suspend fun publish(user: User, bundleBytes: ByteArray, overrides: Overrides = Overrides()): Result {
        val contentId = NanoIdUtils.generateOptimized(
            10,
            "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            63,
            16
        )

        // 1. Extract info.json metadata
        val bundleInfo = DecodingUtils.extractBundleInfo(bundleBytes)

        // 2. Extract cover image (raw bytes) if any
        val coverBytes = DecodingUtils.extractCoverImage(bundleBytes)

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

        // 5. Resolve metadata with override precedence
        val trackName = overrides.track ?: bundleInfo?.title ?: "Unknown"
        val artistName = overrides.artist ?: bundleInfo?.artist ?: "Unknown"

        // 6. Media info enrichment
        val mediaInfo = try { mediaInfoService.getMediaInfo(trackName, artistName) } catch (e: Exception) {
            log.error("Media info fetch failed: ${e.message}")
            null
        }

        // Cover final decision (blank placeholder if we'll attach coverBytes)
        val coverUrlPlaceholder = overrides.coverUrl ?: mediaInfo?.coverUrl ?: ""

        // Streaming links resolution
        val streamingLinks = overrides.trackUrls ?: run {
            try {
                if (mediaInfo?.link != null) {
                    mediaInfoService.getTrackStreamingLinks(mediaInfo.link.url, trackName, artistName)
                } else emptyList()
            } catch (_: Exception) {
                listOfNotNull(mediaInfo?.link)
            }
        }

        log.info("Resolved streaming links: $streamingLinks")

        val bpm = overrides.bpm ?: bundleInfo?.bpm ?: 0
        val isDeluxe = overrides.isDeluxe ?: (bundleInfo?.type?.equals("Promode", ignoreCase = true) ?: false)
        val isExplicit = overrides.isExplicit ?: mediaInfo?.isExplicit ?: false

        // 4. Inject metadata back into the bundle (append computed/enhanced info to info.json)
        val infoToInject = mutableMapOf(
            "contentId" to contentId,
            "duration" to computedStats.duration,
            "notes" to computedStats.notesAmount,
            "effects" to computedStats.effectsAmount,
            // Structure: username|hostId|path|roleId
            "contributors" to "${user.username}|${user.avatarUrl}|0",
            // Structure: host|url
            "cover" to coverUrlPlaceholder,
            "publishedAt" to System.currentTimeMillis(),
        )

        // Optional fields
        if (overrides.previewUrl != null) {
            infoToInject["gameplay"] = overrides.previewUrl
        }

        if (isExplicit) {
            infoToInject["explicit"] = true
        }

        if (streamingLinks.isNotEmpty()) {
            infoToInject["streaming"] = StreamingPlatformUtils.serializeLinks(streamingLinks)
        }

        val enhancedBundleBytes = DecodingUtils.injectInfoToBundle(bundleBytes, infoToInject)

        val createForUpload = CreateChartRequest(
            artist = mediaInfo?.artist ?: artistName,
            track = mediaInfo?.track ?: trackName,
            album = overrides.album ?: mediaInfo?.album,
            trackUrls = streamingLinks,
            // trackPreviewUrl = mediaInfo?.trackPreviewUrl,
            coverUrl = coverUrlPlaceholder,
            genre = overrides.genre ?: mediaInfo?.genre,
            isExplicit = isExplicit,
            duration = computedStats.duration,
            notesAmount = computedStats.notesAmount,
            effectsAmount = computedStats.effectsAmount,
            bpm = bpm,
            difficulty = difficultyEnum,
            isDeluxe = isDeluxe,
            bundleUrl = "",
            previewUrl = overrides.previewUrl,
            contentId = contentId,
        )

        val discordResponse = uploadService.uploadChart(createForUpload, user, enhancedBundleBytes, coverBytes)
        val bundleAttachment = discordResponse.attachments.firstOrNull { it.filename.endsWith(".zip") }
            ?: throw IllegalStateException("Discord response missing bundle attachment")
        val coverUrlFinal = discordResponse.embeds.firstOrNull()?.image?.url ?: createForUpload.coverUrl

        val finalCreate = createForUpload.copy(
            id = discordResponse.id.toULong(),
            versionId = bundleAttachment.id.toULong(),
            bundleUrl = bundleAttachment.url,
            coverUrl = coverUrlFinal,
        )

        val createdChart = chartRepository.createChart(user.id, finalCreate)

        val result = Result(
            chart = createdChart,
            initialVersion = SimplifiedVersion(
                difficulty = finalCreate.difficulty,
                duration = finalCreate.duration,
                notesAmount = finalCreate.notesAmount,
                effectsAmount = finalCreate.effectsAmount,
                isDeluxe = finalCreate.isDeluxe,
                isExplicit = finalCreate.isExplicit,
            ),
            discordMessageId = discordResponse.id,
            versionAttachmentId = bundleAttachment.id,
        )

        activityRepository.logActivity(
            userId = user.id,
            type = ActivityType.CREATED_CHART,
            targetId = createdChart.contentId
        )

        return result
    }
}
