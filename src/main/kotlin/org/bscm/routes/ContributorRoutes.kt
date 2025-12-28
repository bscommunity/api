package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.contributor.CreateContributorRequest
import org.bscm.models.dto.contributor.UpdateContributorRequest
import org.bscm.models.interfaces.IContributorRepository
import java.util.*

fun Route.contributorRoutes(contributorRepository: IContributorRepository) {
    route("/charts") {
        // Add contributors to a chart
        post("{id}/contributors") {
            val id = call.parameters["id"]?.toULong()

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
            val id = call.parameters["id"]?.toULong()
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
            val id = call.parameters["id"]?.toULong()
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
            val id = call.parameters["id"]?.toULong()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@get
            }

            val contributors = contributorRepository.getContributors(id)
            call.respond(contributors)
        }
    }
}
