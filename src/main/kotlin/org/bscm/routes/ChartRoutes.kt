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
import org.bscm.models.KnownIssue
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.dto.contributor.CreateContributorRequest
import org.bscm.models.dto.contributor.UpdateContributorRequest
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.AnalyticsOption
import org.bscm.models.enums.ChartSortOption
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.plugins.HMACPrincipal
import org.bscm.plugins.jsonClient
import org.bscm.repository.ChartRepository
import org.bscm.repository.ContributorRepository
import org.bscm.repository.KnownIssueRepository
import org.bscm.repository.VersionRepository
import org.bscm.services.UploadService
import java.util.*

fun Route.chartRoutes(
    chartRepository: ChartRepository,
    contributorRepository: ContributorRepository,
    knownIssueRepository: KnownIssueRepository,
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

                    val idParam = call.parameters["id"].let { UUID.fromString(it) }
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
                        ?: ChartSortOption.LAST_UPDATED
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()

                    val ids = call.request.queryParameters.getAll("ids")?.map { UUID.fromString(it) }

                    // Determine which type of authentication is being used
                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()

                    // println("JWT Principal: $jwtPrincipal")
                    // println("HMAC Principal: $hmacPrincipal")

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

                    call.respond(charts)
                }

                // Get chart by ID
                get("{id}") {
                    val id = call.parameters["id"]?.let { UUID.fromString(it) }
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
                        jwtPrincipal != null -> chartRepository.getChartById(id)
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
                        ?.map { UUID.fromString(it) } ?: emptyList()
                    val versions = versionRepository.getLatestVersionsByChartIds(chartIds)
                    call.respond(versions)
                }
            }
        }

        // Routes that require JWT authentication only (dashboard operations)
        authenticate("auth-jwt") {
            rateLimit(RateLimitName("protected")) {
                // Create a new chart
                post {
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

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.subject?.let { UUID.fromString(it) }
                        ?: throw Exception("User not authenticated")

                    // Create the chart in the repository
                    val createdChart = chartRepository.createChart(userId, createRequest)

                    println("Created chart: $createdChart")

                    // Upload the chart bundle
                    val bundleId = uploadService.uploadChart(createdChart, bundleFileBytes)
                    println("Successfully uploaded bundle with $bundleId")

                    call.respond(HttpStatusCode.Created, createdChart)
                }

                // Update an existing chart
                put("{id}") {
                    val id = call.parameters["id"]?.let { UUID.fromString(it) }
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
                    val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                        return@delete
                    }

                    val deleted = chartRepository.deleteChart(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent, true)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Chart not found")
                    }
                }
            }

            /* Known Issues ======================================== */

            // Add an issue to a chart
            post("{id}/issues") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@post
                }

                val receivedIssue = call.receive<KnownIssue>()

                val createdIssue = knownIssueRepository.addIssue(id, receivedIssue)
                call.respond(HttpStatusCode.Created, createdIssue)
            }

            // Remove an issue from a chart
            delete("{id}/issues/{issueId}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                val issueId = call.parameters["issueId"]?.let { UUID.fromString(it) }
                if (id == null || issueId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@delete
                }

                val removed = knownIssueRepository.removeIssue(id, issueId)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent, true)
                } else {
                    throw NotFoundException("Chart or issue not found")
                }
            }

            /* Contributor ======================================== */

            // Add contributors to a chart
            post("{id}/contributors") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@post
                }

                val request = call.receive<CreateContributorRequest>()
                val contributors = contributorRepository.addContributors(id, request.contributors)

                call.respond(contributors)
            }

            // Update a contributor's roles
            put("{id}/contributors/{userId}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                val userId = call.parameters["userId"]?.let { UUID.fromString(it) }
                if (id == null || userId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@put
                }

                try {
                    val updatedRequest = call.receive<UpdateContributorRequest>()

                    val updated = contributorRepository.updateContributorRoles(id, userId, updatedRequest.roles)
                    call.respond(updated)
                } catch (e: BadRequestException) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "Bad Request")
                }
            }

            // Remove a contributor from a chart
            delete("{id}/contributors/{userId}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                val userId = call.parameters["userId"]?.let { UUID.fromString(it) }
                if (id == null || userId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@delete
                }

                val removed = contributorRepository.removeContributor(id, userId)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent, "Contributor removed successfully")
                } else {
                    throw NotFoundException("Chart or contributor not found")
                }
            }

            // Get all contributors for a chart
            get("{id}/contributors") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@get
                }

                val contributors = contributorRepository.getContributors(id)
                call.respond(contributors)
            }

            /* Versions ======================================== */

            // Add a version to a chart
            post("{id}/versions") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@post
                }

                val receivedVersion = call.receive<CreateVersionRequest>()

                val createdVersion = versionRepository.addVersion(receivedVersion)
                call.respond(HttpStatusCode.Created, createdVersion)
            }

            // Remove a version from a chart
            delete("{id}/versions/{index}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                val index = call.parameters["index"]?.toInt()

                if (id == null || index == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@delete
                }

                if (index == 0) {
                    call.respond(HttpStatusCode.BadRequest, "Cannot remove the first version")
                    return@delete
                }

                val removed = versionRepository.removeVersion(index, id)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent, "Version removed successfully")
                } else {
                    throw NotFoundException("Chart or version not found")
                }
            }

            // Remove a version from a chart (with id)
            delete("versions/{versionId}") {
                val versionId = call.parameters["versionId"]?.let { UUID.fromString(it) }
                if (versionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@delete
                }

                val removed = versionRepository.removeVersion(versionId)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent, "Version removed successfully")
                } else {
                    throw NotFoundException("Chart or version not found")
                }
            }
        }
    }
}
