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
import org.bscm.models.enums.AnalyticsOption
import org.bscm.models.enums.ChartSortOption
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.plugins.HMACPrincipal
import org.bscm.plugins.UnauthorizedException
import org.bscm.plugins.jsonClient
import org.bscm.repository.ChartRepository
import org.bscm.repository.UserRepository
import org.bscm.repository.VersionRepository
import org.bscm.services.UploadService
import org.bscm.utils.NanoIdUtils
import java.util.*

fun Route.chartRoutes(
    chartRepository: ChartRepository,
    userRepository: UserRepository,
    versionRepository: VersionRepository,
    uploadService: UploadService,
) {
    route("/charts") {
        // Routes that accept both JWT or HMAC authentication
        authenticate("auth-jwt", "auth-hmac", optional = true) {
            rateLimit(RateLimitName("unrestricted")) {
                post("analytics/{id}") {
                    val hmacPrincipal = call.principal<HMACPrincipal>()

                    if (hmacPrincipal == null) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized access")
                        return@post
                    }

                    val idParam = call.parameters["id"]?.toULong() ?: throw BadRequestException("Invalid or missing ID parameter")
                    val typeParam = call.queryParameters["type"]
                    val type = typeParam?.let { AnalyticsOption.valueOf(it) }
                        ?: throw BadRequestException("Invalid or missing type parameter")

                    val stats = chartRepository.postAnalytics(idParam, type)

                    call.respond(stats)
                }

                get("suggestions") {
                    val query = call.request.queryParameters["query"] ?: ""
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 5

                    val suggestions = chartRepository.getSuggestions(query, limit)
                    call.respond(suggestions)
                }
            }

            rateLimit(RateLimitName("protected")) {
                // Get all charts - now handles both auth types
                get {
                    val query = call.request.queryParameters["query"]
                    val sanitizedQuery = query?.replace(Regex("[^a-zA-Z0-9 ]"), "")

                    val difficulties =
                        call.request.queryParameters.getAll("difficulties")?.map { Difficulty.valueOf(it) }
                    val genres = call.request.queryParameters.getAll("genres")?.map { Genre.valueOf(it) }

                    val sortBy = call.request.queryParameters["sortBy"]?.let { ChartSortOption.valueOf(it) }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()

                    val ids = call.request.queryParameters.getAll("ids")?.map { it.toULong() }

                    // Determine which type of authentication is being used
                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()

                    val charts = when {
                        // JWT authentication (dashboard user)
                        jwtPrincipal != null -> {
                            val userId = jwtPrincipal.subject?.let { UUID.fromString(it) }
                            chartRepository.getCharts(
                                userId = userId,
                                chartIds = ids,
                                search = sanitizedQuery,
                                sortBy,
                                difficulties,
                                genres,
                                limit,
                                offset,
                            )
                        }
                        // HMAC authentication (mobile app)
                        hmacPrincipal != null -> {
                            chartRepository.getCharts(
                                chartIds = ids,
                                search = sanitizedQuery,
                                sortBy,
                                difficulties,
                                genres,
                                limit,
                                offset,
                            )
                        }
                        // No authentication (public access)
                        else -> {
                            call.respond(HttpStatusCode.Unauthorized, "Unauthorized access")
                            return@get
                        }
                    }

                    // println("Returning ${charts.size} charts for query: $sanitizedQuery, difficulties: $difficulties, genres: $genres, sortBy: $sortBy, limit: $limit, offset: $offset")

                    call.respond(charts)
                }

                // Get chart by ShareID
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
                            chartRepository.getChartById(id.toULong()) ?: throw BadRequestException("Invalid or missing ID")
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
        authenticate("auth-jwt") {
            rateLimit(RateLimitName("protected")) {
                // Create a new chart
                post {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.subject?.let { UUID.fromString(it) }
                        ?: throw UnauthorizedException("User unauthorized")

                    val user = userRepository.getUserById(userId) ?: throw UnauthorizedException("User not found")

                    // Parse the multipart form data
                    val multipart = call.receiveMultipart()
                    var chartJson: String? = null
                    var bundleFileBytes: ByteArray? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                if (part.name == "chart") {
                                    chartJson = part.value
                                }
                            }
                            is PartData.FileItem -> {
                                if (part.name == "bundle") {
                                    bundleFileBytes = part.provider().toByteArray()
                                }
                            }
                            else -> {}
                        }
                        part.dispose()
                    }

                    if (chartJson == null || bundleFileBytes == null) {
                        call.respond(HttpStatusCode.BadRequest, "Chart data or bundle missing")
                        return@post
                    }

                    // Deserialize the chart JSON
                    val createRequest = try {
                        jsonClient.decodeFromString<CreateChartRequest>(chartJson)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid chart JSON: ${e.message}")
                        return@post
                    }

                    println("Creating chart with request: $createRequest")

                    val shareId = NanoIdUtils.generateOptimized(10, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16)

                    val createRequestWithId = createRequest.copy(
                        shareId = shareId,
                    )

                    // Upload the chart bundle
                    val discordResponse = uploadService.uploadChart(createRequestWithId, user, bundleFileBytes)
                    println("Successfully uploaded bundle with ${discordResponse.id}")

                    val attachment = discordResponse.attachments.firstOrNull()

                    if (attachment == null) {
                        call.respond(HttpStatusCode.BadRequest, "Failed to upload bundle")
                        return@post
                    }

                    val createRequestWithUrl = createRequest.copy(
                        id = discordResponse.id.toULong(),
                        shareId = shareId,
                        versionId = attachment.id.toULong(),
                        bundleUrl = attachment.url
                    )

                    // Create the chart in the repository
                    val createdChart = chartRepository.createChart(userId, createRequestWithUrl)
                    // println("Created chart: $createdChart")

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
