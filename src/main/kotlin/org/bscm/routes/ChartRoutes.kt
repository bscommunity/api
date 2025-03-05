package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.Version
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.dto.contributor.CreateContributorRequest
import org.bscm.models.dto.contributor.UpdateContributorRequest
import org.bscm.repository.ChartRepository
import org.bscm.repository.ContributorRepository
import org.bscm.repository.VersionRepository
import java.util.*

fun Route.chartRoutes(
    chartRepository: ChartRepository,
    contributorRepository: ContributorRepository,
    versionRepository: VersionRepository
) {
    route("/charts") {
        // Get all charts
        get {
            // Check for a "fetchVersions" and "fetchContributors" query parameters
            val fetchVersions = call.request.queryParameters["fetchVersions"]?.toBoolean() ?: false
            val fetchContributors = call.request.queryParameters["fetchContributors"]?.toBoolean() ?: false
            val ids = call.request.queryParameters.getAll("ids")?.map { UUID.fromString(it) } ?: emptyList()

            if (ids.isNotEmpty()) {
                val charts = chartRepository.getCharts(ids, fetchVersions, fetchContributors)
                call.respond(charts)
            } else {
                val charts = chartRepository.getCharts(null, fetchVersions, fetchContributors)
                call.respond(charts)
            }
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
            /*val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                throw IllegalArgumentException("Invalid or missing ID")
            }

            val issue = call.receive<KnownIssue>()
            val added = chartRepository.addIssue(id, issue)
            if (added) {
                call.respond(HttpStatusCode.Created, "Issue added successfully")
            } else {
                throw NotFoundException("Chart not found")
            }*/
        }

        // Remove an issue from a chart
        delete("{id}/issues/{issueId}") {
           /* val id = call.parameters["id"]?.let { UUID.fromString(it) }
            val issueId = call.parameters["issueId"]?.let { UUID.fromString(it) }
            if (id == null || issueId == null) {
                throw IllegalArgumentException("Invalid or missing ID")
            }

            val removed = chartRepository.removeIssue(id, issueId)
            if (removed) {
                call.respond(HttpStatusCode.NoContent, "Issue removed successfully")
            } else {
                throw NotFoundException("Chart or issue not found")
            }*/
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

            val version = call.receive<Version>()

            versionRepository.addVersion(version)
            call.respond(HttpStatusCode.Created, "Version added successfully")
        }

        // Remove a version from a chart
        delete("{id}/versions/{versionId}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            val versionId = call.parameters["versionId"]?.toInt()
            if (id == null || versionId == null) {
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
