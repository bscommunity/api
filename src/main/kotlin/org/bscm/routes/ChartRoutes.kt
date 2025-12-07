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
import org.bscm.services.ChartPublishService
import org.bscm.services.UploadService
import org.koin.ktor.ext.getKoin
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
                    val count = call.request.queryParameters["count"]?.toBoolean() ?: false

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
                                count = count,
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
                                count = count,
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
                                count = count,
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
                                    count = count,
                                )
                            } else {
                                Pair(emptyList(), 0)
                            }
                        }
                    }

                    call.respond(if (count) result else result.first)
                }

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
                            is PartData.FileItem -> if (part.name == "bundle") bundleFileBytes = part.provider().toByteArray()
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
                        try { jsonClient.decodeFromString<CreateChartRequest>(it) } catch (_: Exception) { null }
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