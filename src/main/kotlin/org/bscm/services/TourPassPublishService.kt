package org.bscm.services

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
import org.bscm.storage.StorageService
import org.bscm.utils.StreamingPlatformUtils
import java.util.*

class TourPassPublishService(
    private val tourPassRepository: ITourPassRepository,
    private val chartRepository: IChartRepository,
    private val uploadService: UploadService,
    private val storageService: StorageService,
    private val activityRepository: IActivityRepository,
) {
    suspend fun createAndPublish(
        uploader: User,
        request: CreateTourPassRequest,
        coverBytes: ByteArray?,
        coverContentType: ContentType?,
    ): TourPass {
        if (coverBytes == null && request.coverUrl.isNullOrBlank()) {
            throw BadRequestException("coverUrl or cover file is required")
        }

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

        val tracklist = charts.mapIndexed { index, chart ->
            val icons = buildString {
                if (chart.difficulty == Difficulty.HARD) append(" <:hard:1393411882282385458>")
                if (chart.difficulty == Difficulty.EXTREME) append(" <:extreme:1393411880067797115>")
                if (chart.isDeluxe) append(" <:deluxe:1393402180991586365>")
                if (chart.isExplicit) append(" <:explicit:1393412061786017862>")
            }
            "${index + 1}. ${chart.track.artist} - ${chart.track.title}$icons"
        }

        val normalizedPlaylistUrls = request.playlistUrls
            ?.let { StreamingPlatformUtils.processLinksWithPrioritization(it) }

        // Upload cover to storage if raw bytes were provided
        val coverUrl = if (coverBytes != null) {
            val key = UUID.randomUUID().toString()
            storageService.uploadTourPassCover(key, coverBytes)
            storageService.tourPassCoverUrl(key)
        } else {
            request.coverUrl
        }

        val discordResponse = uploadService.uploadTourPass(
            UploadService.TourPassPublishData(
                title = request.name,
                description = request.description,
                context = UploadService.PublishContext(
                    submittedBy = UploadService.SubmittedBy.fromUser(uploader),
                    trackUrls = normalizedPlaylistUrls ?: emptyList(),
                ),
                coverUrl = coverUrl,
                durationSeconds = charts.sumOf { it.track.duration.toInt() },
                tracksAmount = charts.size,
                tracklist = tracklist,
            )
        )

        val tourPass = tourPassRepository.createTourPass(
            userId = uploader.id,
            name = request.name,
            description = request.description,
            artist = request.artist,
            coverUrl = coverUrl!!,
            playlistUrls = normalizedPlaylistUrls,
            chartIds = chartIds,
            id = discordResponse.id,
        )

        activityRepository.logActivity(
            userId = uploader.id,
            type = ActivityType.CREATED_TOUR_PASS,
            targetId = tourPass.id
        )

        return tourPass
    }

    suspend fun deleteAndCleanup(id: String, userId: UUID): Boolean {
        val contentId = tourPassRepository.getTourPassById(id, userId)?.id ?: return false
        val deleted = tourPassRepository.deleteTourPass(id, userId)
        if (!deleted) return false

        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_TOUR_PASS,
            targetId = contentId
        )

        // Best effort cleanup - DB state is source of truth.
        runCatching { uploadService.deleteMessage(id) }
        return true
    }

    suspend fun updateAndPublish(
        id: String,
        userId: UUID,
        request: UpdateTourPassRequest,
        coverBytes: ByteArray?,
        coverContentType: ContentType?,
    ): TourPass {
        val resolvedCoverUrl = if (coverBytes != null) {
            storageService.uploadTourPassCover(id, coverBytes)
            storageService.tourPassCoverUrl(id)
        } else {
            request.coverUrl
        }

        val normalizedPlaylistUrls = request.playlistUrls
            ?.let { StreamingPlatformUtils.processLinksWithPrioritization(it) }

        return tourPassRepository.updateTourPass(
            id = id,
            userId = userId,
            name = request.name,
            description = request.description,
            artist = request.artist,
            coverUrl = resolvedCoverUrl,
            chartIds = request.chartIds
        )
    }
}
