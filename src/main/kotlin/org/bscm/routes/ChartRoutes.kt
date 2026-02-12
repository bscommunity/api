package org.bscm.routes

import io.klogging.logger
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.openapi.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import org.bscm.clients.jsonClient
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.enums.OperationOption
import org.bscm.models.enums.SortOption
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.plugins.CombinedPrincipal
import org.bscm.plugins.HMACPrincipal
import org.bscm.plugins.UnauthorizedException
import org.bscm.repository.ChartRepository
import org.bscm.services.ChartPublishService
import org.bscm.services.UploadService
import org.koin.ktor.ext.getKoin
import java.util.*

private val log = logger("ChartRoutes")

@OptIn(ExperimentalKtorApi::class)
fun Route.chartRoutes(
    chartRepository: IChartRepository,
    userRepository: IUserRepository,
    versionRepository: IVersionRepository,
    uploadService: UploadService,
    // previewService: PreviewService
) {
    /*
     * CHART ROUTES - CLIENT SCENARIOS
     *
     * 1. MOBILE APP (Workshop Mode):
     *    - Authentication: HMAC (API) + Optional JWT (User)
     *    - Route: GET /charts
     *    - Returns: Public charts + Latest version only + Streaming links
     *    - User Stats: If JWT present → isLiked (CollectionKind.LIKES) & isBookmarked (CollectionKind.BOOKMARKS or USER)
     *
     * 2. WEB APP - WORKSHOP MODE:
     *    - Authentication: JWT (User)
     *    - Route: GET /charts
     *    - Returns: Public charts + Latest version only + NO streaming links
     *    - User Stats: isLiked (CollectionKind.LIKES) & isBookmarked (CollectionKind.BOOKMARKS or USER)
     *
     * 3. WEB APP - DASHBOARD MODE:
     *    - Authentication: JWT (User) required
     *    - Route: GET /me/charts
     *    - Returns: User's charts only (public + private) + All versions + NO streaming links
     *    - User Stats: isLiked (CollectionKind.LIKES) & isBookmarked (CollectionKind.BOOKMARKS or USER)
     *
     * Note: UserContext plugin automatically captures authenticated userId from JWT for populating user stats
     */
    route("/charts") {
        // Routes that accept either JWT or HMAC authentication
        authenticate("auth-public") {
            install(org.bscm.plugins.UserContext)
            rateLimit(RateLimitName("restricted")) {
                /**
                 * List public charts with filtering and search.
                 *
                 * Tag: Charts
                 *
                 * Query: query [String] Optional search query.
                 * Query: difficulties [String] Comma-separated difficulty levels to filter.
                 * Query: genres [String] Comma-separated genres to filter.
                 * Query: versions [String] Filter by chart versions (e.g., "DELUXE").
                 * Query: sortBy [String] Sort option (e.g., "TRENDING", "NEWEST").
                 * Query: limit [Integer] Limit for results (default 20).
                 * Query: offset [Integer] Pagination offset (default 0).
                 * Query: count [Boolean] Include total count in response.
                 *
                 * Responses:
                 *   - 200 application/json [Object] List of charts with metadata. Mobile clients receive streaming links.
                 *   - 401 application/json [Error] Unauthorized access.
                 */
                get {
                    val query = call.request.queryParameters["query"]
                    val sanitizedQuery = query?.replace(Regex("[^a-zA-Z0-9 ]"), "")

                    val difficulties =
                        call.request.queryParameters.getAll("difficulties")?.map { Difficulty.valueOf(it) }
                    val genres = call.request.queryParameters.getAll("genres")?.flatMap { it.split(",") }
                        ?.map { Genre.valueOf(it) }
                    val isDeluxe =
                        call.request.queryParameters.getAll("versions")?.any { it.equals("DELUXE", ignoreCase = true) }

                    val sortBy = call.request.queryParameters["sortBy"]?.let { SortOption.valueOf(it) }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    val count = call.request.queryParameters["count"]?.toBoolean() ?: false

                    // Detect client type from authentication principals
                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    println("jwtPrincipal: ${jwtPrincipal?.subject}, hmacPrincipal: $hmacPrincipal, combinedPrincipal: $combinedPrincipal")

                    // Mobile app includes HMAC authentication and gets streaming links
                    val isMobileApp = hmacPrincipal != null || combinedPrincipal != null

                    val result = if ((offset ?: 0) / (limit ?: 20) >= 5 && jwtPrincipal == null && combinedPrincipal == null) {
                        // Unauthenticated users limited to first 4 pages
                        Pair(emptyList(), null)
                    } else {
                        chartRepository.getCharts(
                            sortBy = sortBy,
                            filters = ChartRepository.ChartFilters(
                                // Workshop mode: userId is null → returns all public charts
                                // UserContext automatically captures authenticated userId for user stats
                                userId = null,
                                search = sanitizedQuery,
                                difficulties = difficulties,
                                genres = genres,
                                isDeluxe = isDeluxe,
                            ),
                            addons = ChartRepository.ChartAddons(
                                // Workshop mode: only latest version
                                allVersions = false,
                                // Only mobile app gets streaming links
                                streamingLinks = isMobileApp,
                                count = count,
                            ),
                            limit = limit,
                            offset = offset,
                        )
                    }

                    call.respond(result)
                }

                /**
                 * Get chart by ID or content ID.
                 *
                 * Tag: Charts
                 *
                 * Path: id [String] Chart ID or content ID (depends on authentication method).
                 *
                 * Responses:
                 *   - 200 application/json [Object] Chart details.
                 *   - 400 application/json [Error] Invalid or missing ID parameter.
                 *   - 401 application/json [Error] Unauthorized access.
                 *   - 404 application/json [Error] Chart not found.
                 */
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
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    val chart = when {
                        jwtPrincipal != null || combinedPrincipal != null -> {
                            chartRepository.getChartById(id.toULong())
                                ?: throw BadRequestException("Invalid or missing ID")
                        }

                        hmacPrincipal != null -> chartRepository.getChartByContentId(id)
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
            }

            rateLimit(RateLimitName("unrestricted")) {
                /**
                 * Get chart search suggestions.
                 *
                 * Tag: Charts
                 *
                 * Query: query [String] Search query string.
                 * Query: limit [Integer] Limit for suggestions (default 5).
                 *
                 * Responses:
                 *   - 200 application/json [Array] List of chart suggestions.
                 */
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
                /**
                 * Record chart analytics event.
                 *
                 * Tag: Charts
                 *
                 * Path: id [ULong] Chart ID.
                 * Query: type [String] Operation type (e.g., "PLAY", "DOWNLOAD").
                 *
                 * Responses:
                 *   - 200 application/json [Object] Updated chart statistics.
                 *   - 400 application/json [Error] Invalid or missing parameters.
                 *
                 * Security: auth-hmac
                 */
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
                /**
                 * Get latest versions of specified charts.
                 *
                 * Tag: Charts
                 *
                 * Query: chartIds [String] Comma-separated chart IDs.
                 *
                 * Responses:
                 *   - 200 application/json [Array] List of latest chart versions.
                 *
                 * Security: auth-hmac
                 */
                get("latest-versions") {
                    val chartIds = call.request.queryParameters["chartIds"]
                        ?.split(",")
                        ?.map { it.toULong() } ?: emptyList()
                    val versions = versionRepository.getLatestVersionsByChartIds(chartIds)
                    log.info("Returning latest versions for chart IDs: $chartIds")
                    call.respond(versions)
                }
            }
        }

        // Routes that require JWT authentication only (dashboard operations)
        authenticate("auth-bearer") {
            rateLimit(RateLimitName("restricted")) {
                post {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.subject?.let { UUID.fromString(it) }
                        ?: throw UnauthorizedException("User unauthorized")

                    val user = userRepository.getUserById(userId) ?: throw UnauthorizedException("User not found")

                    // Parse multipart form (bundle + optional overrides JSON under 'chart')
                    val multipart = call.receiveMultipart()
                    var chartJson: String? = null
                    var bundleFileBytes: ByteArray? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> if (part.name == "chart") chartJson = part.value
                            is PartData.FileItem -> if (part.name == "bundle") bundleFileBytes =
                                part.provider().toByteArray()

                            else -> {}
                        }
                        part.dispose()
                    }

                    if (bundleFileBytes == null) {
                        call.respond(HttpStatusCode.BadRequest, "Bundle missing")
                        return@post
                    }

                    // Optional client overrides
                    val clientRequest: CreateChartRequest? = chartJson?.let {
                        try {
                            jsonClient.decodeFromString<CreateChartRequest>(it)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    val publishService = call.application.getKoin().get<ChartPublishService>()

                    try {
                        val result = publishService.publish(
                            user = user,
                            bundleBytes = bundleFileBytes,
                            overrides = ChartPublishService.Overrides(
                                isExplicit = clientRequest?.isExplicit,
                                previewUrl = clientRequest?.previewUrl,
                            )
                        )
                        call.respond(HttpStatusCode.Created, result.chart)
                    } catch (e: Exception) {
                        println("Chart publish failed: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, e.message ?: "Failed to publish chart")
                    }
                }.describe {
                    tag("Charts")
                    summary = "Create a new chart."
                    description = "Allows authenticated users to create a new chart by uploading a bundle file. Optional metadata can be provided in the 'chart' form field as JSON."
                    requestBody {
                        description = "Chart bundle upload with optional metadata"
                        required = true

                        ContentType.MultiPart.FormData {
                            schema = JsonSchema(
                                type = JsonType.OBJECT,
                                properties = mapOf(
                                    "bundle" to ReferenceOr.Value(JsonSchema(
                                        type = JsonType.STRING,
                                        format = "binary",
                                        description = "The chart bundle file to upload"
                                    )),
                                    "chart" to ReferenceOr.Value(jsonSchema<CreateChartRequest>().copy(
                                        description = "Optional JSON string with chart metadata overrides",
                                    ))
                                ),
                                required = listOf("bundle")
                            )
                        }
                    }
                }

                /**
                 * Update an existing chart's metadata.
                 *
                 * Tag: Charts
                 *
                 * Path: id [ULong] Chart ID.
                 * Body: application/json [UpdateChartRequest] Fields to update.
                 *
                 * Responses:
                 *   - 200 application/json [Object] Updated chart.
                 *   - 400 application/json [Error] Invalid chart ID.
                 *   - 404 application/json [Error] Chart not found.
                 *   - 500 application/json [Error] Internal server error.
                 *
                 * Security: auth-bearer
                 */
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

                /**
                 * Delete a chart.
                 *
                 * Tag: Charts
                 *
                 * Path: id [ULong] Chart ID.
                 *
                 * Responses:
                 *   - 204 Chart deleted successfully.
                 *   - 400 application/json [Error] Invalid chart ID.
                 *   - 401 application/json [Error] User not authenticated or not authorized.
                 *   - 404 application/json [Error] Chart not found.
                 *
                 * Security: auth-bearer
                 */
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

                /**
                 * Get preview for a chart.
                 *
                 * Tag: Charts
                 *
                 * Path: id [ULong] Chart ID.
                 *
                 * Responses:
                 *   - 200 application/json [Object] Chart preview data.
                 *   - 204 No preview available.
                 *   - 501 application/json [Error] Preview service not implemented.
                 *
                 * Security: auth-bearer
                 */
                get("/charts/{id}/preview") {
                    /*val chartId = call.parameters["id"]!!.toLong()
                    val preview = previewService.getPreviewForChart(chartId)

                    if (preview == null) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(preview)
                    }*/
                    call.respond(HttpStatusCode.NotImplemented, "Preview service is not implemented yet")
                }
            }
        }
    }
}