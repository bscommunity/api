package org.bscm.services

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

class TourPassPublishService(
    private val tourPassRepository: ITourPassRepository,
    private val chartRepository: IChartRepository,
    private val uploadService: UploadService,
    private val activityRepository: IActivityRepository,
) {
    suspend fun createAndPublish(
        uploader: User,
        request: CreateTourPassRequest,
        cover: UploadService.UploadImage?,
    ): TourPass {
        if (cover == null && request.coverUrl.isNullOrBlank()) {
            throw BadRequestException("coverUrl or cover file is required")
        }

        val chartIds = request.chartIds?.mapNotNull { it.toULongOrNull() } ?: emptyList()
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
            val latest = chart.latestVersion
            val icons = buildString {
                if (latest?.difficulty == Difficulty.HARD) append(" <:hard:1393411882282385458>")
                if (latest?.difficulty == Difficulty.EXTREME) append(" <:extreme:1393411880067797115>")
                if (latest?.isDeluxe == true) append(" <:deluxe:1393402180991586365>")
                if (latest?.isExplicit == true) append(" <:explicit:1393412061786017862>")
            }
            "${index + 1}. ${chart.artist} - ${chart.track}$icons"
        }

        val discordResponse = uploadService.uploadTourPass(
            UploadService.TourPassPublishData(
                title = request.name,
                description = request.description,
                uploader = uploader,
                coverUrl = request.coverUrl,
                coverImage = cover,
                durationSeconds = charts.sumOf { (it.latestVersion?.duration ?: 0f).toInt() },
                tracksAmount = charts.size,
                tracklist = tracklist,
                trackUrls = charts.flatMap { it.trackUrls },
            )
        )

        val resolvedCoverUrl = discordResponse.attachments.firstOrNull { it.filename.contains("cover", true) }?.url
            ?: discordResponse.embeds.firstOrNull()?.image?.url
            ?: request.coverUrl
            ?: throw IllegalStateException("Unable to resolve cover URL from Discord response")

        val tourPass = tourPassRepository.createTourPass(
            userId = uploader.id,
            name = request.name,
            description = request.description,
            artist = request.artist,
            coverUrl = resolvedCoverUrl,
            chartIds = chartIds,
            id = discordResponse.id.toULong(),
        )

        activityRepository.logActivity(
            userId = uploader.id,
            type = ActivityType.CREATED_TOUR_PASS,
            targetId = tourPass.contentId
        )

        return tourPass
    }

    suspend fun deleteAndCleanup(id: ULong, userId: java.util.UUID): Boolean {
        val contentId = tourPassRepository.getTourPassById(id, userId)?.contentId ?: return false
        val deleted = tourPassRepository.deleteTourPass(id, userId)
        if (!deleted) return false

        activityRepository.removeActivityByTypeAndTarget(
            type = ActivityType.CREATED_TOUR_PASS,
            targetId = contentId
        )

        // Best effort cleanup - DB state is source of truth.
        runCatching { uploadService.deleteMessage(id.toString()) }
        return true
    }

    suspend fun updateAndPublish(
        id: ULong,
        userId: java.util.UUID,
        request: UpdateTourPassRequest,
        cover: UploadService.UploadImage?,
    ): TourPass {
        val resolvedCoverUrl = cover?.let {
            uploadService.uploadCoverImage(
                coverBytes = it.bytes,
                filename = it.filename,
                contentType = it.contentType,
                context = "tourpass-update:$id"
            )
        } ?: request.coverUrl

        return tourPassRepository.updateTourPass(
            id = id,
            userId = userId,
            name = request.name,
            description = request.description,
            artist = request.artist,
            coverUrl = resolvedCoverUrl,
            chartIds = request.chartIds?.mapNotNull { it.toULongOrNull() }
        )
    }
}


