package org.bscm.routes

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.util.logging.*
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
import java.util.*

private val logger = KtorSimpleLogger("ChartRoutes")

@OptIn(ExperimentalKtorApi::class)
fun Route.chartRoutes(
    chartRepository: IChartRepository,
    versionRepository: IVersionRepository,
    userRepository: IUserRepository,
    uploadService: UploadService,
    publishService: ChartPublishService,
) {

    route("/charts") {

        // -----------------------------------------------------------------
        // Public + HMAC routes (workshop browsing, mobile app)
        // -----------------------------------------------------------------
        authenticate("auth-public") {
            install(org.bscm.plugins.UserContext)

            rateLimit(RateLimitName("restricted")) {

                /**
                 * Returns public charts with optional filtering and search.
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
                 * Query: myCharts [Boolean] When true, scope results to the requesting user's own charts (includes private). Requires JWT.
                 *
                 * Responses:
                 *   - 200 application/json [List] List of charts with metadata. Mobile clients receive streaming links.
                 *   - 401 application/json [Error] Unauthorized access.
                 */
                get {
                    val sanitizedQuery = call.request.queryParameters["query"]
                        ?.replace(Regex("[^a-zA-Z0-9 ]"), "")
                        ?.takeIf { it.isNotBlank() }

                    val difficulties = call.request.queryParameters
                        .getAll("difficulties")
                        ?.mapNotNull { runCatching { Difficulty.valueOf(it) }.getOrNull() }

                    val genres = call.request.queryParameters
                        .getAll("genres")
                        ?.flatMap { it.split(",") }
                        ?.mapNotNull { runCatching { Genre.valueOf(it) }.getOrNull() }
                    // Bug fix: Genre.valueOf() throws on unknown values — mapNotNull +
                    // runCatching silently skips bad enum values instead of crashing with 500.

                    val isDeluxe = call.request.queryParameters
                        .getAll("versions")
                        ?.any { it.equals("DELUXE", ignoreCase = true) }

                    val sortBy = call.request.queryParameters["sortBy"]
                        ?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }

                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    val count = call.request.queryParameters["count"]?.toBoolean() ?: false
                    val myCharts = call.request.queryParameters["myCharts"]?.toBoolean() ?: false

                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    // Mobile app authenticates with HMAC or Combined and gets streaming links
                    val isMobileApp = hmacPrincipal != null || combinedPrincipal != null
                    val isAuthenticated = jwtPrincipal != null || combinedPrincipal != null

                    // Resolve the requesting user's ID from JWT or Combined principal.
                    // This is used both for user stats (via UserContext) and for private-chart
                    // visibility: when myCharts=true, passing userId to ChartFilters tells the
                    // repository to include the user's own private charts (isPublic = false)
                    // and scope results to charts they contribute to — matching the same pattern
                    // used in UserRoutes GET {id}/charts with requestingUserId.
                    val requesterId = when {
                        combinedPrincipal != null ->
                            runCatching { UUID.fromString(combinedPrincipal.jwtPrincipal.subject) }.getOrNull()
                        jwtPrincipal != null ->
                            jwtPrincipal.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        else -> null
                    }

                    // userId filter: only scope to the requester's own charts when myCharts=true,
                    // and they are authenticated. Otherwise, browse is public-only (userId=null).
                    val filterUserId = requesterId?.takeIf { myCharts }

                    // Unauthenticated users are limited to the first 5 pages
                    val resolvedOffset = offset ?: 0
                    val resolvedLimit = limit ?: 20
                    val pageIndex = if (resolvedLimit > 0) resolvedOffset / resolvedLimit else 0

                    val result = if (pageIndex >= 5 && !isAuthenticated) {
                        Pair(emptyList(), null)
                    } else {
                        chartRepository.getCharts(
                            sortBy = sortBy,
                            filters = ChartRepository.ChartFilters(
                                userId = filterUserId,
                                search = sanitizedQuery,
                                difficulties = difficulties,
                                genres = genres,
                                isDeluxe = isDeluxe,
                            ),
                            addons = ChartRepository.ChartAddons(
                                versions = false,
                                streamingLinks = isMobileApp,
                                count = count,
                            ),
                            limit = resolvedLimit,
                            offset = resolvedOffset,
                        )
                    }

                    println("Query: $sanitizedQuery, Difficulties: $difficulties, Genres: $genres, IsDeluxe: $isDeluxe, SortBy: $sortBy, Limit: $limit, Offset: $offset, Count: $count, MyCharts: $myCharts, RequesterId: $requesterId, ResultCount: ${result.first.size}")

                    call.respond(result)
                }

                /**
                 * Get chart by internal ID.
                 *
                 * Tag: Charts
                 *
                 * Path: id [ULong] Chart ID.
                 *
                 * Responses:
                 *   - 200 application/json [Object] Chart details.
                 *   - 400 application/json [Error] Invalid or missing ID parameter.
                 *   - 401 application/json [Error] Unauthorized access.
                 *   - 404 application/json [Error] Chart not found.
                 */
                get("{id}") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing chart ID")

                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    if (jwtPrincipal == null && hmacPrincipal == null && combinedPrincipal == null) {
                        throw UnauthorizedException("Unauthorized")
                    }

                    val numericId = id.toULongOrNull()
                        ?: throw BadRequestException("Chart ID must be a valid number")

                    val chart = chartRepository.getChartById(numericId)
                        ?: throw NotFoundException("Chart not found")

                    // Private charts are only visible to their contributors.
                    // HMAC-only callers (mobile app without user context) cannot see private charts.
                    if (!chart.isPublic) {
                        val requesterId = when {
                            combinedPrincipal != null ->
                                runCatching { UUID.fromString(combinedPrincipal.jwtPrincipal.subject) }.getOrNull()
                            jwtPrincipal != null ->
                                jwtPrincipal.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            else -> null
                        }
                        val isContributor = requesterId != null &&
                                chart.contributors.any { contributor -> contributor.user.id == requesterId }
                        if (!isContributor) throw NotFoundException("Chart not found")
                        // Return 404 rather than 403 to avoid leaking chart existence to non-contributors.
                    }

                    call.respond(chart)
                }

                /**
                 * Get chart by content ID.
                 *
                 * Tag: Charts
                 *
                 * Path: id [String] Content ID.
                 *
                 * Responses:
                 *   - 200 application/json [Object] Chart details.
                 *   - 401 application/json [Error] Unauthorized access.
                 *   - 404 application/json [Error] Chart not found.
                 */
                get("content/{id}") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing content ID")

                    call.principal<HMACPrincipal>() ?: throw UnauthorizedException("Unauthorized")

                    val chart = chartRepository.getChartByContentId(id)
                        ?: throw NotFoundException("Chart not found")


                    call.respond(chart)
                }

                /**
                 * Get charts with a list of content IDs.
                 *
                 * Tag: Charts
                 *
                 * Query: ids [String] Comma-separated content IDs.
                 * Responses:
                 *  - 200 application/json [Array] List of chart details.
                 *  - 401 application/json [Error] Unauthorized access.
                 *  - 404 application/json [Error] No charts found for the given content IDs.
                * */
                get("content") {
                    val idsParam = call.request.queryParameters["ids"]
                        ?: throw BadRequestException("Missing content IDs")

                    call.principal<HMACPrincipal>() ?: throw UnauthorizedException("Unauthorized")

                    val contentIds = idsParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    if (contentIds.isEmpty()) {
                        throw BadRequestException("No valid content IDs provided")
                    }

                    val charts = chartRepository.getChartsByContentIds(contentIds)

                    if (charts.isEmpty()) {
                        throw NotFoundException("No charts found for the given content IDs")
                    }

                    call.respond(charts)
                }
            }

            rateLimit(RateLimitName("unrestricted")) {
                /**
                 * Returns search suggestions for the query string.
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
                    call.respond(chartRepository.getSuggestions(query, limit))
                }
            }
        }

        // -----------------------------------------------------------------
        // HMAC-only routes (mobile app analytics + version sync)
        // -----------------------------------------------------------------
        authenticate("auth-public", "auth-hmac") {
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
                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing chart ID")
                    // Bug fix: was toULong() — throws NumberFormatException (500) on bad input.

                    val type = call.queryParameters["type"]
                        ?.let { runCatching { OperationOption.valueOf(it) }.getOrNull() }
                        ?: throw BadRequestException("Invalid or missing operation type")
                    // Bug fix: valueOf() throws on unknown values.

                    call.respond(chartRepository.postAnalytics(id, type))
                }
            }

            rateLimit(RateLimitName("restricted")) {
                /**
                 * Returns the latest version for each of the given chart IDs.
                 * Used by the mobile app to check for updates on installed charts.
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
                        ?.mapNotNull { it.toULongOrNull() }
                        ?: emptyList()

                    logger.info("Fetching latest versions for ${chartIds.size} chart IDs")

                    call.respond(versionRepository.getLatestVersionsByChartIds(chartIds))
                }
            }
        }

        // -----------------------------------------------------------------
        // JWT-only routes (dashboard: create, update, delete)
        // -----------------------------------------------------------------
        authenticate("auth-bearer") {
            rateLimit(RateLimitName("restricted")) {
                post {
                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")
                    // Bug fix: UUID.fromString() throws on malformed JWT subjects.

                    val user = userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    val multipart = call.parseMultipartPayload(
                        acceptedFormFields = setOf("chart"),
                        fileAliases = mapOf("bundle" to "bundle"),
                        defaultFilenames = mapOf("bundle" to "chart-bundle.zip"),
                    ) ?: throw BadRequestException("multipart/form-data is required")

                    val chartJson = multipart.fields["chart"]
                    val bundleFileBytes = multipart.files["bundle"]?.bytes

                    if (bundleFileBytes == null) {
                        throw BadRequestException("Bundle file is required")
                        // Bug fix: was call.respond(BadRequest) + return@post — inconsistent
                        // with the rest of the file which uses throw.
                    }

                    val overrides = chartJson?.let {
                        runCatching { jsonClient.decodeFromString<CreateChartRequest>(it) }
                            .getOrNull()
                        // Silent parse failure is intentional — overrides are optional,
                        // a malformed JSON just means "no overrides".
                    }

                    // Bug fix: was a bare catch(e: Exception) that called println() and
                    // responded with e.message — leaking internal details to the client.
                    // Let the StatusPages plugin handle unexpected exceptions uniformly.
                    val result = publishService.publish(
                        user = user,
                        bundleBytes = bundleFileBytes,
                        overrides = ChartPublishService.Overrides(
                            isExplicit = overrides?.isExplicit,
                            previewUrl = overrides?.previewUrl,
                        )
                    )

                    logger.info("Chart ${result.chart.id} published by user $userId")
                    call.respond(HttpStatusCode.Created, result.chart)

                }.describe {
                    tag("Charts")
                    summary = "Create a new chart."
                    description = "Publish a new chart by uploading a bundle file. Optionally override metadata via the 'chart' JSON form field."
                    requestBody {
                        description = "Chart bundle with optional metadata overrides"
                        required = true
                        ContentType.MultiPart.FormData {
                            schema = JsonSchema(
                                type = JsonType.OBJECT,
                                properties = mapOf(
                                    "bundle" to ReferenceOr.Value(
                                        JsonSchema(
                                            type = JsonType.STRING,
                                            format = "binary",
                                            description = "The chart bundle file to upload"
                                        )
                                    ),
                                    "chart" to ReferenceOr.Value(
                                        jsonSchema<CreateChartRequest>().copy(
                                            description = "Optional JSON string with chart metadata overrides"
                                        )
                                    )
                                ),
                                required = listOf("bundle")
                            )
                        }
                    }
                }

                /**
                 * Updates metadata for an existing chart.
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
                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing chart ID")
                    // Bug fix: was toULong() (500 on bad input) + call.respond + return@put style.

                    val updateRequest = call.receive<UpdateChartRequest>()
                    val updatedChart = chartRepository.updateChart(id, updateRequest)
                    // Let StatusPages handle NotFoundException and any unexpected exceptions
                    // uniformly — no need for a local try/catch here.

                    call.respond(updatedChart)
                }

                /**
                 * Deletes a chart.
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
                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing chart ID")
                    // Bug fix: was toULong() — throws 500 on bad input.

                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")
                    // Bug fix: UUID.fromString() throws on malformed JWT subjects.

                    userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    // Bug fix: order of operations was reversed — Discord was deleted first,
                    // then the DB row. If the DB delete failed, the Discord message was
                    // already gone permanently with no way to recover.
                    //
                    // Correct order: delete from DB first, then clean up Discord.
                    // A failed Discord delete is recoverable (re-run or ignore stale message).
                    // A failed DB delete after Discord cleanup is not.
                    val deleted = publishService.deleteChartAndCleanup(id)

                    if (!deleted) throw NotFoundException("Chart not found")

                    val discordSuccess = runCatching { uploadService.deleteMessage(id.toString()) }
                        .getOrElse { e ->
                            // Discord cleanup failing is non-fatal — the chart is already
                            // removed from the DB. Log and continue rather than returning 500.
                            logger.warn("Chart $id deleted from DB but Discord cleanup failed", e)
                            false
                        }

                    if (!discordSuccess) {
                        logger.warn("Discord message for chart $id could not be deleted — may require manual cleanup")
                    }

                    call.respond(HttpStatusCode.NoContent)
                    // Bug fix: was respond(NoContent, true) — 204 must have no body.
                }

                // Preview endpoint omitted — not yet implemented.
                // Re-add as get("{id}/preview") when ChartPreviewService is ready.
            }
        }
    }
}