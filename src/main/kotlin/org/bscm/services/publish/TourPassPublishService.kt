package org.bscm.services.publish

import io.ktor.http.*
import io.ktor.server.plugins.*
import org.bscm.models.TourPass
import org.bscm.models.User
import org.bscm.models.dto.tourpass.CreateTourPassRequest
import org.bscm.models.dto.tourpass.UpdateTourPassRequest
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.Difficulty
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.repository.ChartRepository
import org.bscm.services.UploadService
import org.bscm.storage.StorageService
import org.bscm.utils.MediaConverter
import org.bscm.utils.NanoIdUtils
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class TourPassPublishService(
    private val tourPassRepository: ITourPassRepository,
    private val chartRepository: IChartRepository,
    private val uploadService: UploadService,
    private val storageService: StorageService,
    private val activityRepository: IActivityRepository,
    private val publishEventService: PublishEventService,
) {
    suspend fun createAndPublish(
        uploader: User,
        request: CreateTourPassRequest,
        coverBytes: ByteArray?,
        coverContentType: ContentType?,
        publishSessionId: String? = null,
    ): TourPass {
        fun emitEvent(step: PublishStep, message: String = step.message) {
            if (publishSessionId != null) {
                publishEventService.emit(publishSessionId, step, message)
            }
        }

        if (coverBytes == null && request.coverUrl.isNullOrBlank()) {
            throw BadRequestException("coverUrl or cover file is required")
        }

        emitEvent(PublishStep.PREPARING_BUNDLE, "Resolving tracklist")

        val chartIds = request.chartIds ?: emptyList()
        val charts = if (chartIds.isNotEmpty()) {
            chartRepository.getCharts(
                filters = ChartRepository.ChartFilters(chartIds = chartIds),
                addons = ChartRepository.ChartAddons(streamingLinks = true),
                limit = null,
                offset = null,
            ).first
        } else {
            emptyList()
        }

        val guildId = uploadService.guildId
        val tracklist = charts.mapIndexed { index, chart ->
            val icons = buildString {
                if (chart.difficulty == Difficulty.HARD) append(" <:hard:1393411882282385458>")
                if (chart.difficulty == Difficulty.EXTREME) append(" <:extreme:1393411880067797115>")
                if (chart.isDeluxe) append(" <:deluxe:1393402180991586365>")
                if (chart.isExplicit) append(" <:explicit:1393412061786017862>")
            }
            val text = "${index + 1}. ${chart.track.artist} - ${chart.track.title}$icons"
            val messageId = chart.discordMessageId
            val channelId = chart.discordChannelId
            if (messageId != null && channelId != null) {
                "[$text](https://discord.com/channels/$guildId/$channelId/$messageId)"
            } else {
                text
            }
        }

        val normalizedPlaylistUrls = request.playlistUrls
            ?.let { StreamingPlatformUtils.processLinksWithPrioritization(it) }

        val catalogId = NanoIdUtils.generateCatalogId()

        // Upload cover to storage if raw bytes were provided
        if (coverBytes != null) {
            emitEvent(PublishStep.UPLOADING_COVER, "Uploading tour pass cover")
            val avifBytes = MediaConverter.convertToAvif(coverBytes) ?: coverBytes
            storageService.uploadTourPassCover(catalogId, avifBytes)
        }

        val difficultyLabel = if (charts.isNotEmpty()) {
            val totalScore = charts.sumOf { chart ->
                val base = when (chart.difficulty) {
                    Difficulty.HARD -> 2.0
                    Difficulty.EXTREME -> 3.0
                    else -> 1.0
                }
                base + if (chart.isDeluxe) 1.0 else 0.0
            }
            val avg = totalScore / charts.size
            val label = when {
                avg >= 4.0 -> "Very Extreme"
                avg >= 3.5 -> "Extreme"
                avg >= 3.0 -> "Slightly Extreme"
                avg >= 2.5 -> "Very Hard"
                avg >= 2.0 -> "Hard"
                avg >= 1.5 -> "Slightly Hard"
                else -> "Normal"
            }
            val hasHard = charts.any { it.difficulty == Difficulty.HARD || it.isDeluxe }
            val hasExtreme = charts.any { it.difficulty == Difficulty.EXTREME }
            buildString {
                append(label)
                if (label.contains("Hard") && hasHard) append(" <:hard:1393411882282385458>")
                if (label.contains("Extreme") && hasExtreme) append(" <:extreme:1393411880067797115>")
            }
        } else null

        val tourPassData = UploadService.TourPassPublishData(
            title = request.name,
            description = request.description,
            context = UploadService.PublishContext(
                catalogId = catalogId,
                submittedBy = UploadService.SubmittedBy.fromUser(uploader),
                trackUrls = normalizedPlaylistUrls ?: emptyList(),
            ),
            coverUrl = storageService.tourPassCoverUrl(catalogId),
            durationSeconds = charts.sumOf { it.track.duration.toInt() },
            tracksAmount = charts.size,
            difficultyLabel = difficultyLabel,
            trailerUrl = request.previewUrl,
            tracklist = tracklist,
        )

        emitEvent(PublishStep.UPLOADING_TO_DISCORD, "Uploading tour pass to Discord")
        val discordResponse = uploadService.uploadTourPass(tourPassData)

        emitEvent(PublishStep.CREATING_CHART, "Creating tour pass in database")

        val tourPass = try {
            suspendTransaction {
                val created = tourPassRepository.createTourPass(
                    userId = uploader.id,
                    name = request.name,
                    description = request.description,
                    artist = request.artist,
                    playlistUrls = normalizedPlaylistUrls,
                    chartIds = chartIds,
                    id = catalogId,
                )

                tourPassRepository.updateDiscordCoordinates(
                    catalogId,
                    discordResponse.channelId,
                    discordResponse.id,
                )

                created
            }
        } catch (e: Exception) {
            runCatching { uploadService.deleteMessage(discordResponse.id) }
            throw e
        }

        emitEvent(PublishStep.LOGGING_ACTIVITY)

        activityRepository.logActivity(
            userId = uploader.id,
            type = ActivityType.CREATED_TOUR_PASS,
            targetId = tourPass.id
        )

        emitEvent(PublishStep.COMPLETED)

        return tourPass
    }

    suspend fun deleteAndCleanup(id: String, userId: UUID): Boolean {
        val tourPass = tourPassRepository.getTourPassById(id, userId) ?: return false
        if (tourPass.authorId != userId) {
            throw SecurityException("You are not the author of this tour pass")
        }
        val deleted = tourPassRepository.deleteTourPass(id, userId)
        if (!deleted) return false

        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_TOUR_PASS,
            targetId = tourPass.id
        )

        // Best effort cleanup - DB state is source of truth.
        runCatching { storageService.deleteTourPassCover(id) }
        tourPass.discordMessageId?.let { messageId ->
            runCatching { uploadService.deleteMessage(messageId) }
        }
        return true
    }

    suspend fun updateAndPublish(
        id: String,
        userId: UUID,
        request: UpdateTourPassRequest,
        coverBytes: ByteArray?,
        coverContentType: ContentType?,
    ): TourPass {
        if (coverBytes != null) {
            val avifBytes = MediaConverter.convertToAvif(coverBytes) ?: coverBytes
            storageService.uploadTourPassCover(id, avifBytes)
        }

        val normalizedPlaylistUrls = request.playlistUrls
            ?.let { StreamingPlatformUtils.processLinksWithPrioritization(it) }

        return tourPassRepository.updateTourPass(
            id = id,
            userId = userId,
            name = request.name,
            description = request.description,
            artist = request.artist,
            chartIds = request.chartIds
        )
    }
}
