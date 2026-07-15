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
import org.bscm.protobuf.ChartParser
import org.bscm.storage.StorageService
import org.bscm.utils.DecodingUtils
import org.bscm.utils.NanoIdUtils

private val log = KtorSimpleLogger("ChartPublishService")

/** Centralized pipeline for publishing a chart (Discord upload + DB persist). */
class ChartPublishService(
    private val chartRepository: IChartRepository,
    private val uploadService: UploadService,
    private val storageService: StorageService,
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
     * Returns the Discord message ID for cleanup.
     * @throws NotFoundException if the chart does not exist.
     */
    suspend fun deleteChartAndCleanup(chartId: String): String {
        val chart = chartRepository.getChartById(chartId)
            ?: throw NotFoundException("Chart not found")
        val discordMessageId = chart.discordMessageId
            ?: throw IllegalStateException("Chart $chartId has no Discord message ID")
        chartRepository.deleteChart(chartId)
        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_CHART,
            targetId = chartId
        )
        return discordMessageId
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

        // 2. Extract cover image from bundle (will be uploaded to storage after track creation)
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

        // 4. Resolve metadata with override precedence
        val trackName = overrides.track ?: bundleInfo?.title ?: "Unknown"
        val artistName = overrides.artist ?: bundleInfo?.artist ?: "Unknown"

        // 5. Media info enrichment
        val mediaInfo = try { mediaInfoService.getMediaInfo(trackName, artistName) } catch (e: Exception) {
            log.error("Media info fetch failed: ${e.message}")
            null
        }

        val coverUrl = overrides.coverUrl ?: mediaInfo?.coverUrl ?: ""

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

        // 6. Create DB entities first (without version)
        val createForDb = CreateChartRequest(
            artist = mediaInfo?.artist ?: artistName,
            track = mediaInfo?.track ?: trackName,
            album = overrides.album ?: mediaInfo?.album,
            trackUrls = streamingLinks,
            coverUrl = coverUrl,
            genre = overrides.genre ?: mediaInfo?.genre,
            isExplicit = isExplicit,
            duration = computedStats.duration,
            notesAmount = computedStats.notesAmount,
            effectsAmount = computedStats.effectsAmount,
            bpm = bpm,
            difficulty = difficultyEnum,
            isDeluxe = isDeluxe,
            bundleUrl = "",
            fileSizeBytes = bundleBytes.size.toLong(),
            previewUrl = overrides.previewUrl,
            contentId = contentId,
        )

        val createdChart = chartRepository.createChart(user.id, createForDb)

        log.info("Created chart ${createdChart.id}")

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

        // 9. Upload enriched bundle to Discord (single call)
        val discordResponse = uploadService.uploadChart(createForDb, user, enrichedBundleBytes)
        val bundleAttachment = discordResponse.attachments.firstOrNull { it.filename.endsWith(".zip") }
            ?: throw IllegalStateException("Discord response missing bundle attachment")

        // 9b. Persist Discord message/channel IDs so delete/edit operations use the snowflake
        chartRepository.updateDiscordCoordinates(
            catalogItemId = createdChart.id,
            channelId = discordResponse.channelId,
            messageId = discordResponse.id,
        )

        // 10. Finalize: add version with Discord bundle URL
        chartRepository.addVersion(
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

        // 11. Upload cover image to storage
        if (coverBytes != null) {
            try {
                storageService.uploadTrackCover(createdChart.track.id, coverBytes)
            } catch (e: Exception) {
                log.warn("Failed to upload track cover to storage: ${e.message}")
            }
        }

        val result = Result(
            chart = createdChart,
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

        activityRepository.logActivity(
            userId = user.id,
            type = ActivityType.CREATED_CHART,
            targetId = createdChart.id
        )

        return result
    }
}
