package org.bscm.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.bscm.clients.jsonClient
import org.bscm.models.dto.tourpass.CreateTourPassRequest
import org.bscm.models.dto.tourpass.UpdateTourPassRequest
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.Difficulty
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.repository.ChartRepository
import org.bscm.services.UploadService
import org.bscm.utils.getUserId
import org.bscm.utils.getUserIdOrNull
import java.util.*

private data class TourPassCreatePayload(
    val request: CreateTourPassRequest,
    val cover: UploadedImage?,
)

private data class TourPassUpdatePayload(
    val request: UpdateTourPassRequest,
    val cover: UploadedImage?,
)

private suspend fun ApplicationCall.receiveTourPassCreatePayload(): TourPassCreatePayload {
    if (!request.contentType().match(ContentType.MultiPart.FormData)) {
        return TourPassCreatePayload(receive(), null)
    }

    val multipart = receiveMultipart()
    var requestJson: String? = null
    var cover: UploadedImage? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> if (part.name == "tourPass") requestJson = part.value
            is PartData.FileItem -> if (part.name == "cover") {
                val bytes = part.provider().toByteArray()
                if (bytes.isNotEmpty()) {
                    cover = UploadedImage(
                        bytes = bytes,
                        filename = part.originalFileName ?: "tourpass-cover.png",
                        contentType = part.contentType ?: ContentType.Application.OctetStream,
                    )
                }
            }
            else -> {}
        }
        part.dispose()
    }

    val body = requestJson ?: throw BadRequestException("tourPass field is required for multipart requests")
    val request = runCatching { jsonClient.decodeFromString<CreateTourPassRequest>(body) }
        .getOrElse { throw BadRequestException("Invalid tourPass JSON payload") }

    return TourPassCreatePayload(request, cover)
}

private suspend fun ApplicationCall.receiveTourPassUpdatePayload(): TourPassUpdatePayload {
    if (!request.contentType().match(ContentType.MultiPart.FormData)) {
        return TourPassUpdatePayload(receive(), null)
    }

    val multipart = receiveMultipart()
    var requestJson: String? = null
    var cover: UploadedImage? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> if (part.name == "tourPass") requestJson = part.value
            is PartData.FileItem -> if (part.name == "cover") {
                val bytes = part.provider().toByteArray()
                if (bytes.isNotEmpty()) {
                    cover = UploadedImage(
                        bytes = bytes,
                        filename = part.originalFileName ?: "tourpass-cover.png",
                        contentType = part.contentType ?: ContentType.Application.OctetStream,
                    )
                }
            }
            else -> {}
        }
        part.dispose()
    }

    val body = requestJson ?: throw BadRequestException("tourPass field is required for multipart requests")
    val request = runCatching { jsonClient.decodeFromString<UpdateTourPassRequest>(body) }
        .getOrElse { throw BadRequestException("Invalid tourPass JSON payload") }

    return TourPassUpdatePayload(request, cover)
}

fun Route.tourPassRoutes(
    tourPassRepository: ITourPassRepository,
    activityRepository: IActivityRepository,
    uploadService: UploadService,
    userRepository: IUserRepository,
    chartRepository: IChartRepository,
) {
    route("/tourpasses") {
        authenticate("auth-bearer", optional = true) {
            rateLimit(RateLimitName("unrestricted")) {
                /**
                 * List tour passes with optional search and filtering.
                 * Tag: TourPasses
                 *
                 * Query: search [String] Optional search string to filter tour passes.
                 * Query: limit [Integer] Optional limit for results.
                 * Query: offset [Integer] Optional pagination offset.
                 * Query: ids [String] Comma-separated tour pass IDs to retrieve.
                 *
                 * Response: 200 application/json List of tour passes.
                 */
                get {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("sub")?.asString()?.let { UUID.fromString(it) }

                    val search = call.request.queryParameters["search"]
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    val ids = call.request.queryParameters.getAll("ids")

                    val tourPasses = tourPassRepository.getTourPasses(
                        userId = userId,
                        contentIds = ids,
                        search = search,
                        limit = limit,
                        offset = offset
                    )

                    call.respond(tourPasses)
                }

                /**
                 * Get tour pass by ID.
                 * Tag: TourPasses
                 *
                 * Path: id [ULong] Tour pass ID.
                 *
                 * Responses:
                 *   - 400 ID parameter is malformatted or missing.
                 *   - 404 Tour pass not found.
                 *   - 200 Tour pass details.
                 */
                get("/{id}") {
                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing ID parameter")

                    val tourPass = tourPassRepository.getTourPassById(id, call.getUserIdOrNull())
                        ?: throw NotFoundException("TourPass not found")

                    call.respond(tourPass)
                }

                /**
                 * Get tour pass by content ID.
                 * Tag: TourPasses
                 *
                 * Path: contentId [String] Content ID.
                 *
                 * Responses:
                 *   - 400 Missing contentId parameter.
                 *   - 404 Tour pass not found.
                 *   - 200 Tour pass details.
                 */
                get("/content/{id}") {
                    val contentId = call.parameters["id"]
                        ?: throw BadRequestException("Missing contentId parameter")

                    val tourPass = tourPassRepository.getAppTourPassById(contentId, call.getUserIdOrNull())
                        ?: throw NotFoundException("TourPass not found")

                    call.respond(tourPass)
                }
            }
        }

        authenticate("auth-bearer") {
            rateLimit(RateLimitName("restricted")) {
                /**
                 * Create a new tour pass.
                 * Tag: TourPasses
                 *
                 * Security: auth-bearer
                 *
                 * Body: application/json Tour pass name, artist, and cover URL [CreateTourPassRequest].
                 *
                 * Response: 201 application/json Created tour pass.
                 * Response: 400 application/json Authentication required or invalid request.
                 */
                post {
                    val userId = call.getUserId()
                    val user = userRepository.getUserById(userId)
                        ?: throw NotFoundException("User not found")

                    val payload = call.receiveTourPassCreatePayload()
                    val request = payload.request
                    if (payload.cover == null && request.coverUrl.isNullOrBlank()) {
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
                            uploader = user,
                            coverUrl = request.coverUrl,
                            coverImage = payload.cover?.let {
                                UploadService.UploadImage(it.bytes, it.filename, it.contentType)
                            },
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
                        userId = userId,
                        name = request.name,
                        description = request.description,
                        artist = request.artist,
                        coverUrl = resolvedCoverUrl,
                        chartIds = chartIds,
                        id = discordResponse.id.toULong(),
                    )

                    activityRepository.logActivity(
                        userId = userId,
                        type = ActivityType.CREATED_TOUR_PASS,
                        targetId = tourPass.contentId
                    )

                    call.respond(HttpStatusCode.Created, tourPass)
                }

                route("/{id}") {
                    /**
                     * Update an existing tour pass.
                     * Tag: TourPasses
                     *
                     * Path: id [ULong] Tour pass ID.
                     * Body: application/json Fields to update [UpdateTourPassRequest].
                     *
                     * Responses:
                     *   - 400 ID parameter is malformatted or missing, or authentication required.
                     *   - 200 Updated tour pass.
                     */
                    put {
                        val userId = call.getUserId()

                        val id = call.parameters["id"]?.toULongOrNull()
                            ?: throw BadRequestException("Invalid or missing ID parameter")

                        val payload = call.receiveTourPassUpdatePayload()
                        val request = payload.request
                        val resolvedCoverUrl = payload.cover?.let {
                            uploadService.uploadCoverImage(
                                coverBytes = it.bytes,
                                filename = it.filename,
                                contentType = it.contentType,
                                context = "tourpass-update:$id"
                            )
                        } ?: request.coverUrl

                        val tourPass = tourPassRepository.updateTourPass(
                            id = id,
                            userId = userId,
                            name = request.name,
                            description = request.description,
                            artist = request.artist,
                            coverUrl = resolvedCoverUrl,
                            chartIds = request.chartIds?.mapNotNull { it.toULongOrNull() }
                        )

                        call.respond(tourPass)
                    }

                    /**
                     * Delete a tour pass.
                     * Tag: TourPasses
                     *
                     * Path: id [ULong] Tour pass ID.
                     *
                     * Responses:
                     *   - 400 ID parameter is malformatted or missing, or authentication required.
                     *   - 404 Tour pass not found.
                     *   - 204 Tour pass deleted successfully.
                     */
                    delete {
                        val userId = call.getUserId()

                        val id = call.parameters["id"]?.toULongOrNull()
                            ?: throw BadRequestException("Invalid or missing ID parameter")

                        val contentId = tourPassRepository.getTourPassById(id, userId)?.contentId
                            ?: throw NotFoundException("TourPass not found")

                        val success = tourPassRepository.deleteTourPass(id, userId)
                        if (success) {
                            activityRepository.removeActivityByTypeAndTarget(
                                type = ActivityType.CREATED_TOUR_PASS,
                                targetId = contentId
                            )
                            runCatching { uploadService.deleteMessage(id.toString()) }
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            throw NotFoundException("TourPass not found")
                        }
                    }

                    put("/charts") {
                        val userId = call.getUserId()
                        val id = call.parameters["id"]?.toULongOrNull()
                            ?: throw BadRequestException("Invalid or missing ID parameter")

                        val chartIds = call.receive<List<String>>()
                            .mapNotNull { it.toULongOrNull() }

                        val updated = tourPassRepository.setTourPassCharts(id, userId, chartIds)
                        call.respond(updated)
                    }

                    route("/charts/{chartId}") {

                        /**
                         * Add chart to tour pass.
                         * Tag: TourPasses
                         *
                         * Path: id [ULong] Tour pass ID.
                         * Path: chartId [ULong] Chart ID.
                         *
                         * Responses:
                         *   - 400 Invalid parameters or chart already in tour pass.
                         *   - 401 Authentication required.
                         *   - 200 Success message.
                         */
                        post {
                            val userId = call.getUserId()

                            val tourPassId = call.parameters["id"]?.toULongOrNull()
                                ?: throw BadRequestException("Invalid or missing tour pass ID parameter")

                            tourPassRepository.getTourPassById(tourPassId, userId)
                                ?: throw NotFoundException("TourPass not found")

                            val chartId = call.parameters["chartId"]?.toULongOrNull()
                                ?: throw BadRequestException("Invalid or missing chart ID parameter")

                            val success = tourPassRepository.addChartToTourPass(tourPassId, chartId)
                            if (success) {
                                call.respond(
                                    HttpStatusCode.OK,
                                    mapOf("message" to "Chart added to tour pass successfully")
                                )
                            } else {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "Chart already in tour pass or tour pass not found")
                                )
                            }
                        }

                        /**
                         * Remove chart from tour pass.
                         * Tag: TourPasses
                         *
                         * Path: id [ULong] Tour pass ID.
                         * Path: chartId [ULong] Chart ID.
                         *
                         * Responses:
                         *   - 400 Invalid parameters or authentication required.
                         *   - 404 Chart not found in tour pass.
                         *   - 200 Success message.
                         */
                        delete {
                            val userId = call.getUserId()

                            val tourPassId = call.parameters["id"]?.toULongOrNull()
                                ?: throw BadRequestException("Invalid or missing tour pass ID parameter")

                            tourPassRepository.getTourPassById(tourPassId, userId)
                                ?: throw NotFoundException("TourPass not found")

                            val chartId = call.parameters["chartId"]?.toULongOrNull()
                                ?: throw BadRequestException("Invalid or missing chart ID parameter")

                            val success = tourPassRepository.removeChartFromTourPass(tourPassId, chartId)
                            if (success) {
                                call.respond(
                                    HttpStatusCode.OK,
                                    mapOf("message" to "Chart removed from tour pass successfully")
                                )
                            } else {
                                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Chart not found in tour pass"))
                            }
                        }
                    }
                }
            }
        }
    }
}
