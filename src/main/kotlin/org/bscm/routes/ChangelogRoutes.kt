package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.changelog.CreateChangelogEntryRequest
import org.bscm.models.dto.changelog.CreateChangelogEntryResponse
import org.bscm.models.interfaces.IChangelogRepository
import java.util.*

fun Route.changelogRoutes(changelogRepository: IChangelogRepository) {
    route("/changelog/{catalogItemId}/issues") {

        authenticate("auth-bearer") {
            post {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")

                val request = call.receive<CreateChangelogEntryRequest>()

                val entryId = changelogRepository.addIssue(catalogItemId, request.description)

                call.respond(HttpStatusCode.Created, CreateChangelogEntryResponse(entryId))
            }

            delete("{issueId}") {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")
                val issueId = call.parameters["issueId"]?.let { UUID.fromString(it) }
                    ?: throw BadRequestException("Invalid or missing issue ID")

                val removed = changelogRepository.removeIssue(catalogItemId, issueId)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    throw NotFoundException("Catalog item or issue not found")
                }
            }
        }
    }
}
