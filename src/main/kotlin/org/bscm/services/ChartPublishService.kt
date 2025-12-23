package org.bscm.services

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.bscm.models.Chart
import org.bscm.models.StreamingLink
import org.bscm.models.User
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.version.SimplifiedVersion
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.repository.IChartRepository
import org.bscm.protobuf.ChartParser
import org.bscm.utils.NanoIdUtils

/** Centralized pipeline for publishing a chart (Discord upload + DB persist). */
class ChartPublishService(
    private val chartRepository: IChartRepository,
    private val uploadService: UploadService,
    private val supportUploadService: UploadService,
    private val mediaInfoService: MediaInfoService
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
        val initialVersion: SimplifiedVersion,
        val discordMessageId: String,
        val versionAttachmentId: String,
    )

    private suspend fun downloadAudioFile(url: String): ByteArray {
        val response: HttpResponse = org.bscm.plugins.applicationHttpClient.get(url)
        if (!response.status.isSuccess()) {
            throw Exception("Failed to download audio file: ${response.status}")
        }
        return response.readRawBytes()
    }

    private fun getNormalizedTrackName(track: String): String {
        val normalized = track.trim()
            .lowercase(java.util.Locale.getDefault())
            .replace(Regex("\\s*\\([^)]*\\)"), "")   // remove (...)
            .replace(Regex("\\s*\\[.*?]"), "")       // remove [...]
            .replace(Regex("\\s*[Ff]eat\\..*"), "")  // remove feat...
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")    // replace non-alfa numeric with space
            .replace(Regex("\\s+"), "_")             // collapse whitespace → underscore
            .trim('_')                               // trim leading/trailing underscores
        return normalized
    }

    suspend fun publish(user: User, bundleBytes: ByteArray, overrides: Overrides = Overrides()): Result {
        val contentId = NanoIdUtils.generateOptimized(
            10,
            "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            63,
            16
        )

        // 1. Extract info.json metadata
        val bundleInfo = DecodingService.extractBundleInfo(bundleBytes)

        // 2. Extract cover image (raw bytes) if any
        val coverBytes = DecodingService.extractCoverImage(bundleBytes)

        // 3. Extract chart.bytes and parse
        val chartBytes = DecodingService.extractChartFileFromBundle(bundleBytes)
            ?: throw IllegalStateException("Failed to extract chart.bytes from bundle")
        val parsedProto = ChartParser.parse(chartBytes)
        val computedStats = DecodingService.computeChartStats(parsedProto, bundleInfo?.bpm)

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
            println("Media info fetch failed: ${e.message}")
            null
        }

        // Cover final decision (blank placeholder if we'll attach coverBytes)
        val coverUrlPlaceholder = if (coverBytes != null) "" else overrides.coverUrl ?: mediaInfo?.coverUrl ?: ""

        // Streaming links resolution
        val streamingLinks = overrides.trackUrls ?: run {
            try {
                if (!mediaInfo?.trackUrls.isNullOrEmpty()) {
                    mediaInfoService.getTrackStreamingLinks(mediaInfo.trackUrls.first().url, trackName, artistName)
                } else mediaInfo?.trackUrls ?: emptyList()
            } catch (_: Exception) {
                mediaInfo?.trackUrls ?: emptyList()
            }
        }

        println("Resolved streaming links: $streamingLinks")

        val bpm = overrides.bpm ?: bundleInfo?.bpm ?: 0
        val isDeluxe = overrides.isDeluxe ?: (bundleInfo?.type?.equals("Promode", ignoreCase = true) ?: false)
        val isExplicit = overrides.isExplicit ?: mediaInfo?.isExplicit ?: false
        val previewUrl = overrides.previewUrl

        // 4. Inject metadata back into the bundle (append computed/enhanced info to info.json)
        val infoToInject = mutableMapOf(
            "contentId" to contentId,
            "duration" to computedStats.duration,
            "notes" to computedStats.notesAmount,
            "effects" to computedStats.effectsAmount,
            "contributors" to "${user.username}#author",
            "publishedAt" to System.currentTimeMillis(),
        )

        // Optional fields
        if (!previewUrl.isNullOrBlank() || mediaInfo?.trackPreviewUrl != null) {
            infoToInject["previewUrl"] = previewUrl ?: mediaInfo?.trackPreviewUrl!!
        }

        if (isExplicit) {
            infoToInject["isExplicit"] = true
        }

        if (streamingLinks.isNotEmpty()) {
            val linksForInfo = streamingLinks.map { "${it.platform}#${it.url}" }
            infoToInject["streamingLinks"] = linksForInfo.joinToString(";")
        }

        val enhancedBundleBytes = DecodingService.injectInfoToBundle(bundleBytes, infoToInject)

        val createForUpload = CreateChartRequest(
            artist = mediaInfo?.artist ?: artistName,
            track = mediaInfo?.track ?: trackName,
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

        return Result(
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
    }
}
