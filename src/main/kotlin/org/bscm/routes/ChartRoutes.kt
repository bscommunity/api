package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.KnownIssue
import org.bscm.models.dto.AddContributorRequest
import org.bscm.models.dto.CreateChartRequest
import org.bscm.models.dto.UpdateChartRequest
import org.bscm.repository.ChartRepository
import java.util.*

fun Route.chartRoutes(chartRepository: ChartRepository) {
    authenticate("auth-jwt") {
        route("/charts") {
            // Get all charts
            get {
                // Check for a "fetchContributors" query parameter
                val fetchContributors = call.request.queryParameters["fetchContributors"]?.toBoolean() ?: false

                val charts = chartRepository.getAllCharts(fetchContributors)
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

            // Create a new chart
            post {
                val createRequest = call.receive<CreateChartRequest>()
                println(createRequest)

                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subject?.let { UUID.fromString(it) } ?: throw Exception("User not authenticated")

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
                    call.respond(HttpStatusCode.OK, "Chart deleted successfully")
                } else {
                    throw NotFoundException("Chart not found")
                }
            }

            // Add an issue to a chart
            post("{id}/issues") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }

                val issue = call.receive<KnownIssue>()
                val added = chartRepository.addIssue(id, issue)
                if (added) {
                    call.respond(HttpStatusCode.Created, "Issue added successfully")
                } else {
                    throw NotFoundException("Chart not found")
                }
            }

            // Remove an issue from a chart
            delete("{id}/issues/{issueId}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                val issueId = call.parameters["issueId"]?.let { UUID.fromString(it) }
                if (id == null || issueId == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }

                val removed = chartRepository.removeIssue(id, issueId)
                if (removed) {
                    call.respond(HttpStatusCode.OK, "Issue removed successfully")
                } else {
                    throw NotFoundException("Chart or issue not found")
                }
            }

            // Add a contributor to a chart
            post("{id}/contributors") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }

                val request = call.receive<AddContributorRequest>()
                println(request)

                val added = chartRepository.addContributor(id, request.userId)
                if (added) {
                    call.respond(HttpStatusCode.Created, "Contributor added successfully")
                } else {
                    throw NotFoundException("Chart or user not found")
                }
            }

            // Remove a contributor from a chart
            delete("{id}/contributors/{userId}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                val userId = call.parameters["userId"]?.let { UUID.fromString(it) }
                if (id == null || userId == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }

                val removed = chartRepository.removeContributor(id, userId)
                if (removed) {
                    call.respond(HttpStatusCode.OK, "Contributor removed successfully")
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

                val contributors = chartRepository.getContributors(id)
                call.respond(contributors)
            }

            // Add a version to a chart
            post("{id}/versions") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }

                val added = chartRepository.addVersion(id)
                if (added) {
                    call.respond(HttpStatusCode.Created, "Version added successfully")
                } else {
                    throw NotFoundException("Chart not found")
                }
            }

            // Remove a version from a chart
            delete("{id}/versions/{versionId}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                val versionId = call.parameters["versionId"]?.let { UUID.fromString(it) }
                if (id == null || versionId == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }

                val removed = chartRepository.removeVersion(id, versionId)
                if (removed) {
                    call.respond(HttpStatusCode.OK, "Version removed successfully")
                } else {
                    throw NotFoundException("Chart or version not found")
                }
            }
        }
    }
}
