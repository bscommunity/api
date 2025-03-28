package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.KnownIssue
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.dto.contributor.CreateContributorRequest
import org.bscm.models.dto.contributor.UpdateContributorRequest
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.ChartSortOption
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.repository.ChartRepository
import org.bscm.repository.ContributorRepository
import org.bscm.repository.KnownIssueRepository
import org.bscm.repository.VersionRepository
import java.util.*

fun Route.chartRoutes(
    chartRepository: ChartRepository,
    contributorRepository: ContributorRepository,
    knownIssueRepository: KnownIssueRepository,
    versionRepository: VersionRepository
) {
    route("/charts") {
        // Get all charts
        get {
            // Check for a "fetchVersions" and "fetchContributors" query parameters
            val fetchVersions = call.request.queryParameters["fetchVersions"]?.toBoolean() ?: false
            val fetchContributors = call.request.queryParameters["fetchContributors"]?.toBoolean() ?: false

            val query = call.request.queryParameters["query"]
            val sanitizedQuery = query?.replace(Regex("[^a-zA-Z0-9 ]"), "")

            val difficulties = call.request.queryParameters.getAll("difficulties")?.map { Difficulty.valueOf(it) }
            val genres = call.request.queryParameters.getAll("genres")?.map { Genre.valueOf(it) }

            val sortBy = call.request.queryParameters["sortBy"]?.let { ChartSortOption.valueOf(it) }
                ?: ChartSortOption.LAST_UPDATED
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()
            val offset = call.request.queryParameters["offset"]?.toIntOrNull()

            val ids = call.request.queryParameters.getAll("ids")?.map { UUID.fromString(it) }

            val charts = chartRepository.getCharts(
                ids,
                sanitizedQuery,
                sortBy,
                difficulties,
                genres,
                limit,
                offset,
                fetchVersions,
                fetchContributors
            )
            call.respond(charts)
        }

        // Get chart by ID
        get("{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                throw IllegalArgumentException("Invalid or missing ID")
            }

            val chart = chartRepository.getChartById(id)
            if (chart != null) {
                call.respond(chart)
            } else {
                throw NotFoundException("Chart not found")
            }
        }

        get("suggestions") {
            val query = call.request.queryParameters["query"] ?: ""
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 5

            val suggestions = chartRepository.getSuggestions(query, limit)
            call.respond(suggestions)
        }

        authenticate("auth-jwt") {
            // Create a new chart
            post {
                val createRequest = call.receive<CreateChartRequest>()
                println(createRequest)

                val principal = call.principal<JWTPrincipal>()

                println("Principal: $principal")

                val userId =
                    principal?.subject?.let { UUID.fromString(it) } ?: throw Exception("User not authenticated")

                val createdChart = chartRepository.createChart(userId, createRequest)
                call.respond(HttpStatusCode.Created, createdChart)
            }

            // Update an existing chart
            put("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }

                val updateRequest = call.receive<UpdateChartRequest>()

                try {
                    val updatedChart = chartRepository.updateChart(id, updateRequest)
                    call.respond(updatedChart)
                } catch (e: NotFoundException) {
                    throw NotFoundException(e.message ?: "Not Found")
                } catch (e: Exception) {
                    throw Exception(e.message ?: "Internal Server Error")
                }
            }

            // Delete a chart
            delete("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }

                val deleted = chartRepository.deleteChart(id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent, true)
                } else {
                    throw NotFoundException("Chart not found")
                }
            }
        }

        /* Known Issues ======================================== */

        // Add an issue to a chart
        post("{id}/issues") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                throw IllegalArgumentException("Invalid or missing ID")
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
                throw IllegalArgumentException("Invalid or missing ID")
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
                throw IllegalArgumentException("Invalid or missing ID")
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
                throw IllegalArgumentException("Invalid or missing ID")
            }

            try {
                val updatedRequest = call.receive<UpdateContributorRequest>()

                val updated = contributorRepository.updateContributorRoles(id, userId, updatedRequest.roles)
                call.respond(updated)
            } catch (e: BadRequestException) {
                throw BadRequestException(e.message ?: "Bad Request")
            }
        }

        // Remove a contributor from a chart
        delete("{id}/contributors/{userId}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            val userId = call.parameters["userId"]?.let { UUID.fromString(it) }
            if (id == null || userId == null) {
                throw IllegalArgumentException("Invalid or missing ID")
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
                throw IllegalArgumentException("Invalid or missing ID")
            }

            val contributors = contributorRepository.getContributors(id)
            call.respond(contributors)
        }

        /* Versions ======================================== */

        get("latest-versions") {
            val chartIds = call.request.queryParameters.getAll("chartIds")?.map { UUID.fromString(it) } ?: emptyList()
            val versions = versionRepository.getLatestVersionsByChartIds(chartIds)
            call.respond(versions)
        }

        // Add a version to a chart
        post("{id}/versions") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                throw IllegalArgumentException("Invalid or missing ID")
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
                throw IllegalArgumentException("Invalid or missing ID")
            }

            if (index == 0) {
                throw IllegalArgumentException("Cannot remove the first version")
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
                throw IllegalArgumentException("Invalid or missing ID")
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
