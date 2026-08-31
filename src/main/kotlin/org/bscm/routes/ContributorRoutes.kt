package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.contributor.CreateContributorRequest
import org.bscm.models.dto.contributor.UpdateContributorRequest
import org.bscm.models.enums.ContributorRole
import org.bscm.models.interfaces.IContributorRepository
import org.bscm.utils.getUserId
import java.util.*

fun Route.contributorRoutes(contributorRepository: IContributorRepository) {
    route("/contributors/{catalogItemId}") {

        get {
            val catalogItemId = call.parameters["catalogItemId"]
                ?: throw BadRequestException("Invalid or missing catalog item ID")

            val contributors = contributorRepository.getContributors(catalogItemId)
            call.respond(contributors)
        }

        authenticate("auth-bearer") {
            post {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")

                val request = call.receive<CreateContributorRequest>()
                val contributors = contributorRepository.addContributors(catalogItemId, request.contributors)

                call.respond(contributors)
            }

            delete("self") {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")
                val userId = call.getUserId()

                val removed = contributorRepository.removeContributor(catalogItemId, userId, null)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    throw NotFoundException("Contributor not found")
                }
            }

            put("{userId}") {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")
                val userId = call.parameters["userId"]?.let { UUID.fromString(it) }
                    ?: throw BadRequestException("Invalid or missing user ID")

                val updatedRequest = call.receive<UpdateContributorRequest>()
                val updated = contributorRepository.updateContributorRoles(catalogItemId, userId, updatedRequest.roles)
                call.respond(updated)
            }

            delete("{userId}") {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")
                val userId = call.parameters["userId"]?.let { UUID.fromString(it) }
                    ?: throw BadRequestException("Invalid or missing user ID")
                val role = call.request.queryParameters["role"]
                    ?.let { runCatching { ContributorRole.valueOf(it) }.getOrNull() }

                val removed = contributorRepository.removeContributor(catalogItemId, userId, role)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    throw NotFoundException("Content or contributor not found")
                }
            }
        }
    }
}
