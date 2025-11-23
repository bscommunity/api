package org.bscm.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.enums.OperationOption
import org.bscm.models.enums.SortOption
import org.bscm.models.repository.IChartRepository
import org.bscm.models.repository.IUserRepository
import org.bscm.models.repository.IVersionRepository
import org.bscm.plugins.CombinedPrincipal
import org.bscm.plugins.HMACPrincipal
import org.bscm.plugins.UnauthorizedException
import org.bscm.plugins.jsonClient
import org.bscm.protobuf.ChartParser
import org.bscm.services.DecodingService
import org.bscm.services.MediaInfoService
import org.bscm.services.UploadService
import org.bscm.services.safeExtractCoverImage
import java.util.*

fun Route.chartRoutes(
    chartRepository: IChartRepository,
    userRepository: IUserRepository,
    versionRepository: IVersionRepository,
    uploadService: UploadService,
) {
    route("/charts") {
        // Routes that accept either JWT or HMAC authentication
        authenticate("auth-public") {
            rateLimit(RateLimitName("restricted")) {
                // Get all charts - handles both mobile app and dashboard
                get {
                    val query = call.request.queryParameters["query"]
                    val sanitizedQuery = query?.replace(Regex("[^a-zA-Z0-9 ]"), "")

                    val difficulties =
                        call.request.queryParameters.getAll("difficulties")?.map { Difficulty.valueOf(it) }
                    val genres = call.request.queryParameters.getAll("genres")?.flatMap { it.split(",") }?.map { Genre.valueOf(it) }
                    val isDeluxe =  call.request.queryParameters.getAll("versions")?.any { it.equals("DELUXE", ignoreCase = true) }

                    val isDashboard = call.request.queryParameters["isDashboard"]?.toBoolean()

                    val sortBy = call.request.queryParameters["sortBy"]?.let { SortOption.valueOf(it) }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()

                    // Try to get both principals
                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    // println("JWT Principal: $jwtPrincipal, HMAC Principal: $hmacPrincipal, Combined Principal: $combinedPrincipal")

                    val result = when {
                        // Combined auth (HMAC with JWT for user-specific data)
                        combinedPrincipal != null -> {
                            val userId = UUID.fromString(combinedPrincipal.jwtPrincipal.subject)
                            chartRepository.getAppCharts(
                                userId = if (isDashboard == true) userId else null,
                                search = sanitizedQuery,
                                sortBy = sortBy,
                                difficulties = difficulties,
                                genres = genres,
                                isDeluxe = isDeluxe,
                                limit = limit,
                                offset = offset,
                            )
                        }
                        // Mobile app (HMAC only, no user context)
                        hmacPrincipal != null -> {
                            chartRepository.getAppCharts(
                                userId = null,
                                search = sanitizedQuery,
                                sortBy = sortBy,
                                difficulties = difficulties,
                                genres = genres,
                                isDeluxe = isDeluxe,
                                limit = limit,
                                offset = offset,
                            )
                        }
                        // JWT authentication (dashboard user)
                        jwtPrincipal != null -> {
                            val userId = jwtPrincipal.subject?.let { UUID.fromString(it) }
                            chartRepository.getFullCharts(
                                userId = if (isDashboard == true) userId else null,
                                search = sanitizedQuery,
                                sortBy = sortBy,
                                difficulties = difficulties,
                                genres = genres,
                                isDeluxe = isDeluxe,
                                limit = limit,
                                offset = offset,
                            )
                        }

                        // No authentication - limit to first 4 pages of app charts
                        else -> {
                            val page = (offset ?: 0) / (limit ?: 20) + 1
                            if (page < 5) {
                                chartRepository.getAppCharts(
                                    userId = null,
                                    search = sanitizedQuery,
                                    sortBy = sortBy,
                                    difficulties = difficulties,
                                    genres = genres,
                                    isDeluxe = isDeluxe,
                                    limit = limit,
                                    offset = offset,
                                )
                            } else {
                                Pair(emptyList(), 0)
                            }
                        }
                    }

                    call.respond(result)
                }
            }

            rateLimit(RateLimitName("unrestricted")) {
                get("suggestions") {
                    val query = call.request.queryParameters["query"] ?: ""
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 5

                    val suggestions = chartRepository.getSuggestions(query, limit)
                    call.respond(suggestions)
                }
            }
        }

        // Routes that accept HMAC authentication only
        authenticate("auth-hmac") {
            rateLimit(RateLimitName("unrestricted")) {
                post("analytics/{id}") {
                    val idParam =
                        call.parameters["id"]?.toULong() ?: throw BadRequestException("Invalid or missing ID parameter")
                    val typeParam = call.queryParameters["type"]
                    val type = typeParam?.let { OperationOption.valueOf(it) }
                        ?: throw BadRequestException("Invalid or missing type parameter")

                    val stats = chartRepository.postAnalytics(idParam, type)

                    call.respond(stats)
                }
            }

            rateLimit(RateLimitName("restricted")) {
                // Get chart by content id
                get("{id}") {
                    val id = call.parameters["id"]
                    if (id == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Invalid or missing ID"
                        )
                        return@get
                    }

                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()

                    val chart = when {
                        jwtPrincipal != null -> {
                            chartRepository.getChartById(id.toULong())
                                ?: throw BadRequestException("Invalid or missing ID")
                        }

                        hmacPrincipal != null -> chartRepository.getAppChartById(id)
                        else -> {
                            call.respond(HttpStatusCode.Unauthorized, "Unauthorized access")
                            return@get
                        }
                    }

                    if (chart != null) {
                        call.respond(chart)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Chart not found")
                        return@get
                    }
                }

                // Get latest versions of charts by IDs
                get("latest-versions") {
                    val chartIds = call.request.queryParameters["chartIds"]
                        ?.split(",")
                        ?.map { it.toULong() } ?: emptyList()
                    val versions = versionRepository.getLatestVersionsByChartIds(chartIds)
                    println("Returning latest versions for chart IDs: $chartIds")
                    call.respond(versions)
                }
            }
        }

        // Routes that require JWT authentication only (dashboard operations)
        authenticate("auth-bearer") {
            rateLimit(RateLimitName("restricted")) {
                // Create a new chart
                post {
                    // ================= PIPELINE OVERVIEW =================
                    // FORM FIELDS: previewUrl, isExplicit
                    // ZIP (BUNDLE) DECODE: cover image PNG (Texture2D), chart.bytes (TextAsset)
                    // INFO.JSON: track, artist, difficulty, isDeluxe, bpm
                    // MEDIA SERVICE: album, trackUrls, trackPreviewUrl, genre
                    // INTERNAL CALCS: duration, notesAmount, effectsAmount (from parsed chart)
                    // AFTER DISCORD UPLOAD: bundleUrl (zip attachment), coverUrl (cover attachment)
                    // =====================================================

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.subject?.let { UUID.fromString(it) }
                        ?: throw UnauthorizedException("User unauthorized")

                    val user = userRepository.getUserById(userId) ?: throw UnauthorizedException("User not found")

                    // Parse multipart form
                    val multipart = call.receiveMultipart()
                    var chartJson: String? = null
                    var bundleFileBytes: ByteArray? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> if (part.name == "chart") chartJson = part.value
                            is PartData.FileItem -> if (part.name == "bundle") bundleFileBytes = part.provider().toByteArray()
                            else -> {}
                        }
                        part.dispose()
                    }

                    if (bundleFileBytes == null) {
                        call.respond(HttpStatusCode.BadRequest, "Bundle missing")
                        return@post
                    }

                    // Try to decode provided chart JSON (optional overrides from client)
                    val clientRequest: CreateChartRequest? = chartJson?.let {
                        try { jsonClient.decodeFromString<CreateChartRequest>(it) } catch (_: Exception) { null }
                    }

                    // Extract previewUrl & isExplicit early (source-of-truth: form JSON)
                    val previewUrlFromForm = clientRequest?.previewUrl
                    val isExplicitFromForm = clientRequest?.isExplicit ?: false

                    // 1. Extract info.json metadata
                    val bundleInfo = DecodingService.extractBundleInfo(bundleFileBytes)

                    // 2. Extract cover image (raw bytes) if any
                    val coverBytes = DecodingService.Companion.safeExtractCoverImage(bundleFileBytes)

                    // 3. Extract chart.bytes from chart.bundle and parse protobuf
                    val chartBytes = DecodingService.extractChartFileFromBundle(bundleFileBytes)
                    if (chartBytes == null) {
                        call.respond(HttpStatusCode.BadRequest, "Failed to extract chart.bundle bytes")
                        return@post
                    }
                    val parsed = ChartParser.parse(chartBytes)
                    val computedStats = DecodingService.computeChartStats(parsed, bundleInfo?.bpm)

                    // 4. Derive difficulty from bundleInfo.difficulty mapping to enum
                    val difficultyEnum = when (bundleInfo?.difficulty) {
                        4 -> Difficulty.NORMAL
                        3 -> Difficulty.HARD
                        1 -> Difficulty.EXTREME
                        else -> Difficulty.NORMAL
                    }

                    // 5. Fetch media info (album cover, streaming links) using track + artist from bundleInfo overrides
                    val trackName = bundleInfo?.title ?: clientRequest?.track ?: "Unknown"
                    val artistName = bundleInfo?.artist ?: clientRequest?.artist ?: "Unknown"
                    val mediaInfo = try { MediaInfoService.getMediaInfo(trackName, artistName) } catch (error: Exception) {
                        println("MediaInfo fetch error: ${error.message}")
                        null
                    }

                    println("MediaInfo fetched: $mediaInfo")

                    // Fallback cover: if extracted coverBytes available, upload later; else use mediaInfo.coverUrl
                    val coverUrlPlaceholder = if (coverBytes != null) "" else (mediaInfo?.coverUrl ?: clientRequest?.coverUrl ?: "")

                    // 6. Track URLs (streaming links). If mediaInfo returned some, optionally enrich them with Odesli
                    val streamingLinks = try {
                        if (!mediaInfo?.trackUrls.isNullOrEmpty()) {
                            MediaInfoService.getTrackStreamingLinks(mediaInfo.trackUrls.first().url, trackName, artistName)
                        } else clientRequest?.trackUrls ?: emptyList()
                    } catch (error: Exception) {
                        println("Streaming links fetch error: ${error.message}")
                        mediaInfo?.trackUrls ?: clientRequest?.trackUrls ?: emptyList()
                    }

                    // 7. BPM: prefer bundleInfo.bpm else clientRequest else approximate (not implemented)
                    val bpm = bundleInfo?.bpm ?: clientRequest?.bpm ?: 0

                    // 8. isDeluxe flag from bundle type
                    val isDeluxe = bundleInfo?.type?.equals("Promode", ignoreCase = true) ?: clientRequest?.isDeluxe ?: false

                    // 9. Generate contentId
                    val contentId = org.bscm.utils.NanoIdUtils.generateOptimized(
                        10,
                        "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
                        63,
                        16
                    )

                    // 10. Upload bundle to Discord (cover image uploading not yet implemented separately)
                    // println("Bundle size: ${bundleFileBytes.size}")
                    val createChartForUpload = CreateChartRequest(
                        artist = artistName,
                        track = trackName,
                        album = mediaInfo?.album,
                        trackUrls = streamingLinks,
                        trackPreviewUrl = mediaInfo?.trackPreviewUrl,
                        coverUrl = coverUrlPlaceholder,
                        genre = mediaInfo?.genre,
                        isExplicit = isExplicitFromForm,
                        duration = computedStats.duration,
                        notesAmount = computedStats.notesAmount,
                        effectsAmount = computedStats.effectsAmount,
                        bpm = bpm,
                        difficulty = difficultyEnum,
                        isDeluxe = isDeluxe,
                        bundleUrl = "",
                        previewUrl = previewUrlFromForm,
                        contentId = contentId,
                    )

                    val discordResponse = uploadService.uploadChart(createChartForUpload, user,
                        bundleFileBytes, coverBytes)

                    // Identify attachments by extension
                    val bundleAttachment = discordResponse.attachments.firstOrNull { it.filename.endsWith(".zip") }
                    val coverUrl = discordResponse.embeds.firstOrNull()?.image?.url

                    // println("Discord attachments: ${discordResponse.attachments}")

                    if (bundleAttachment == null) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to upload bundle")
                        return@post
                    }

                    val finalCreate = createChartForUpload.copy(
                        id = discordResponse.id.toULong(),
                        versionId = bundleAttachment.id.toULong(),
                        bundleUrl = bundleAttachment.url,
                        coverUrl = coverUrl ?: createChartForUpload.coverUrl,
                    )

                    val createdChart = chartRepository.createChart(userId, finalCreate)

                    call.respond(HttpStatusCode.Created, createdChart)
                }

                // Update an existing chart
                put("{id}") {
                    val id = call.parameters["id"]?.toULong()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                        return@put
                    }

                    val updateRequest = call.receive<UpdateChartRequest>()

                    try {
                        val updatedChart = chartRepository.updateChart(id, updateRequest)
                        call.respond(updatedChart)
                    } catch (e: NotFoundException) {
                        call.respond(HttpStatusCode.NotFound, e.message ?: "Not Found")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal Server Error")
                    }
                }

                // Delete a chart
                delete("{id}") {
                    val id = call.parameters["id"]?.toULong()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                        return@delete
                    }

                    // Check if the user is authorized to delete the chart
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.subject?.let { UUID.fromString(it) }
                        ?: throw UnauthorizedException("User unauthorized")

                    // Fetch the user to ensure they exist
                    userRepository.getUserById(userId) ?: throw UnauthorizedException("User not found")

                    // Try to delete the message from Discord
                    val success = uploadService.deleteMessage(id.toString())

                    if (!success) throw Exception("Failed to delete chart with id: $id")

                    val deleted = chartRepository.deleteChart(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent, true)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Chart not found")
                    }
                }
            }
        }
    }
}