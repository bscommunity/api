package org.bscm.services

import org.bscm.models.Chart
import org.bscm.models.StreamingLink
import org.bscm.models.User
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.repository.IChartRepository
import org.bscm.protobuf.ChartParser
import org.bscm.utils.NanoIdUtils

/** Centralized pipeline for publishing a chart (Discord upload + DB persist). */
class ChartPublishService(
    private val chartRepository: IChartRepository,
    private val uploadService: UploadService,
) {
    data class Overrides(
        val track: String? = null,
        val artist: String? = null,
        val isExplicit: Boolean? = null,
        val previewUrl: String? = null,
        val coverUrl: String? = null,
        val bpm: Int? = null,
        val isDeluxe: Boolean? = null,
        val trackUrls: List<StreamingLink>? = null,
        val album: String? = null,
        val genre: Genre? = null,
    )

    data class Result(
        val chart: Chart,
        val discordMessageId: String,
        val versionAttachmentId: String,
    )

    suspend fun publish(user: User, bundleBytes: ByteArray, overrides: Overrides = Overrides()): Result {
        // 1. Extract info.json metadata
        val bundleInfo = DecodingService.extractBundleInfo(bundleBytes)

        // 2. Extract cover image (raw bytes) if any
        val coverBytes = DecodingService.extractCoverImage(bundleBytes)

        // 3. Extract chart.bytes and parse
        val chartBytes = DecodingService.extractChartFileFromBundle(bundleBytes)
            ?: throw IllegalStateException("Failed to extract chart.bytes from bundle")
        val parsedProto = ChartParser.parse(chartBytes)
        val computedStats = DecodingService.computeChartStats(parsedProto, bundleInfo?.bpm)

        // 4. Derive difficulty
        val difficultyEnum = when (bundleInfo?.difficulty) {
            4 -> Difficulty.NORMAL
            3 -> Difficulty.HARD
            1 -> Difficulty.EXTREME
            else -> Difficulty.NORMAL
        }

        // 5. Resolve metadata with override precedence
        val trackName = overrides.track ?: bundleInfo?.title ?: "Unknown"
        val artistName = overrides.artist ?: bundleInfo?.artist ?: "Unknown"

        // 6. Media info enrichment (only if overrides not fully provided)
        val mediaInfo = try { MediaInfoService.getMediaInfo(trackName, artistName) } catch (_: Exception) { null }

        // Cover final decision (blank placeholder if we'll attach coverBytes)
        val coverUrlPlaceholder = if (coverBytes != null) "" else overrides.coverUrl ?: mediaInfo?.coverUrl ?: ""

        // Streaming links resolution
        val streamingLinks = overrides.trackUrls ?: run {
            try {
                if (!mediaInfo?.trackUrls.isNullOrEmpty()) {
                    MediaInfoService.getTrackStreamingLinks(mediaInfo.trackUrls.first().url, trackName, artistName)
                } else mediaInfo?.trackUrls ?: emptyList()
            } catch (_: Exception) {
                mediaInfo?.trackUrls ?: emptyList()
            }
        }

        val bpm = overrides.bpm ?: bundleInfo?.bpm ?: 0
        val isDeluxe = overrides.isDeluxe ?: (bundleInfo?.type?.equals("Promode", ignoreCase = true) ?: false)
        val isExplicit = overrides.isExplicit ?: false
        val previewUrl = overrides.previewUrl

        val contentId = NanoIdUtils.generateOptimized(
            10,
            "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            63,
            16
        )

        val createForUpload = CreateChartRequest(
            artist = artistName,
            track = trackName,
            album = overrides.album ?: mediaInfo?.album,
            trackUrls = streamingLinks,
            trackPreviewUrl = mediaInfo?.trackPreviewUrl,
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
            previewUrl = previewUrl,
            contentId = contentId,
        )

        val discordResponse = uploadService.uploadChart(createForUpload, user, bundleBytes, coverBytes)
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

        return Result(
            chart = createdChart,
            discordMessageId = discordResponse.id,
            versionAttachmentId = bundleAttachment.id,
        )
    }
}

