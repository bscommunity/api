package org.bscm.services.publish

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.util.logging.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.bscm.models.Chart
import org.bscm.models.StreamingRef
import org.bscm.models.User
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.dto.version.SimplifiedVersion
import org.bscm.models.dto.version.VersionBundleData
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IChartRepository
import org.bscm.plugins.ConflictException
import org.bscm.protobuf.ChartParser
import org.bscm.repository.AlbumRepository
import org.bscm.services.AudioPreviewService
import org.bscm.services.UploadService
import org.bscm.services.track.TrackInfoService
import org.bscm.services.track.clients.applicationHttpClient
import org.bscm.storage.StorageService
import org.bscm.utils.DecodingUtils
import org.bscm.utils.MediaConverter
import org.bscm.utils.NanoIdUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
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
    private val publishEventService: PublishEventService,
    private val albumRepository: AlbumRepository,
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
        val contributors: List<SimplifiedContributor>? = null,
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

        runCatching { storageService.deleteTrackPreview(chart.track.id) }

        chartRepository.deleteChart(chartId)
        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_CHART,
            targetId = chartId
        )
        return chart.discordMessageId
    }


    suspend fun publish(user: User, bundleBytes: ByteArray, overrides: Overrides = Overrides(), publishSessionId: String? = null): Result = coroutineScope {
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

        val catalogId = NanoIdUtils.generateCatalogId()

        // 1. Extract info.json metadata
        val bundleInfo = DecodingUtils.extractBundleInfo(bundleBytes)

        // 2. Extract cover image from bundle (may be overridden by COVER_SOURCE after media info)
        val bundleCoverBytes = DecodingUtils.extractCoverImage(bundleBytes)

        // emitEvent(PublishStep.PARSING_CHART)

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

        // Resolve cover bytes based on COVER_SOURCE
        val coverBytes = when (COVER_SOURCE) {
            CoverSource.BUNDLE -> bundleCoverBytes
            CoverSource.MEDIA_INFO -> {
                val mediaCoverUrl = overrides.coverUrl ?: mediaInfo?.coverUrl
                if (mediaCoverUrl.isNullOrBlank()) {
                    bundleCoverBytes
                } else {
                    try {
                        val imgResponse: HttpResponse = applicationHttpClient.get(mediaCoverUrl)
                        if (imgResponse.status.isSuccess()) imgResponse.readRawBytes() else bundleCoverBytes
                    } catch (e: Exception) {
                        log.warn("Failed to download media cover, falling back to bundle: ${e.message}")
                        bundleCoverBytes
                    }
                }
            }
        }

        // 5b. Resolve album entity — always create one so cover has a home
        val albumName = overrides.album ?: mediaInfo?.album ?: "$trackName – Single"
        val albumEntity = albumRepository.findOrCreate(albumName)

        // Fire cover upload in background — the deterministic URL is already computable
        // from albumEntity.id, so nothing downstream needs the bytes to have landed.
        val coverUploadJob = coverBytes?.let { bytes ->
            launch {
                try {
                    val avifBytes = MediaConverter.convertToAvif(bytes) ?: bytes
                    storageService.uploadAlbumCover(albumEntity.id.value, avifBytes)
                } catch (e: Exception) {
                    log.warn("Failed to upload album cover to storage: ${e.message}")
                }
            }
        }

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
        }.toMutableList()
        val resolvedIsrc = mediaInfo?.isrc ?: streamingResult.isrc

        // Collect album-level streaming refs from media info and MusicBrainz
        val albumStreamingRefs = buildList {
            mediaInfo?.albumStreamingRefs?.let { addAll(it) }
            addAll(streamingResult.albumRefs)
        }

        // Store album streaming refs if album entity exists
        if (albumStreamingRefs.isNotEmpty()) {
            albumRepository.attachStreamingRefs(albumEntity.id.value, albumStreamingRefs)
        }

        log.info("Resolved streaming links: $streamingLinks")

        val bpm = overrides.bpm ?: bundleInfo?.bpm ?: 0
        val isDeluxe = overrides.isDeluxe ?: (bundleInfo?.type?.equals("Promode", ignoreCase = true) ?: false)
        val isExplicit = overrides.isExplicit ?: mediaInfo?.isExplicit ?: false

        val createForDb = CreateChartRequest(
            artist = mediaInfo?.artist ?: artistName,
            track = mediaInfo?.track ?: trackName,
            album = albumName,
            albumId = albumEntity.id.value,
            trackUrls = streamingLinks,
            coverUrl = storageService.albumCoverUrl(albumEntity.id.value),
            genres = (overrides.genres ?: mediaInfo?.genres).orEmpty(),
            isExplicit = isExplicit,
            duration = computedStats.duration,
            notesAmount = computedStats.notesAmount,
            effectsAmount = computedStats.effectsAmount,
            bpm = bpm,
            difficulty = difficultyEnum,
            isDeluxe = isDeluxe,
            fileSizeBytes = bundleBytes.size.toLong(),
            bundleHash = bundleHash,
            previewUrl = overrides.previewUrl,
            catalogId = catalogId,
            isrc = resolvedIsrc,
            contributors = overrides.contributors.orEmpty(),
        )

        // Build enriched bundle before Discord upload (catalogId doubles as chart ID)
        val coverCdnUrl = storageService.albumCoverUrl(albumEntity.id.value)
        val bscmMetadata = DecodingUtils.BscmMetadata(
            chartId = catalogId,
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

        // Upload enriched bundle to Discord before any DB writes
        emitEvent(PublishStep.UPLOADING_TO_DISCORD)
        val discordResponse = uploadService.uploadChart(createForDb, user, enrichedBundleBytes, coverBytes)
        val bundleAttachment = discordResponse.attachments.firstOrNull { it.filename.endsWith(".zip") }
            ?: throw IllegalStateException("Discord response missing bundle attachment")

        // Single transaction for all DB writes (chart, Discord coords, version)
        emitEvent(PublishStep.CREATING_CHART)
        val (createdChart, version) = try {
            suspendTransaction {
                val chart = chartRepository.createChart(user.id, createForDb)
                log.info("Created chart ${chart.id}")

                chartRepository.updateDiscordCoordinates(
                    catalogItemId = chart.id,
                    channelId = discordResponse.channelId,
                    messageId = discordResponse.id,
                )

                emitEvent(PublishStep.FINALIZING_VERSION)

                val v = chartRepository.addVersion(
                    catalogItemId = chart.id,
                    version = VersionBundleData(
                        id = bundleAttachment.id.toULong(),
                        fileSizeBytes = createForDb.fileSizeBytes,
                        changelog = "",
                    ),
                    bundleHash = bundleHash,
                )
                Pair(chart, v)
            }
        } catch (e: Exception) {
            runCatching { uploadService.deleteMessage(discordResponse.id) }
            throw e
        }

        val updatedChart = createdChart.copy(
            latestVersion = version,
            versions = listOf(version),
            versionsCount = createdChart.versionsCount + 1,
        )

        emitEvent(PublishStep.GENERATING_PREVIEW)

        // 12. Download, convert, and upload audio preview to storage
        if (mediaInfo != null) {
            audioPreviewService.publish(createdChart.track.id, mediaInfo)
        }

        // Cover must exist on CDN by the time we return — the UI shows it immediately.
        coverUploadJob?.join()

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

        result
    }
}
