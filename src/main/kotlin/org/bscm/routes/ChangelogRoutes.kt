package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.changelog.CreateChangelogEntryRequest
import org.bscm.models.dto.changelog.CreateChangelogEntryResponse
import org.bscm.models.interfaces.IChangelogRepository
import java.util.*

fun Route.changelogRoutes(changelogRepository: IChangelogRepository) {
    route("/charts") {
        post("{id}/issues") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@post
            }

            val request = call.receive<CreateChangelogEntryRequest>()

            val entryId = changelogRepository.addIssue(id, request.description)

            call.respond(HttpStatusCode.Created, CreateChangelogEntryResponse(entryId))
        }

        delete("{id}/issues/{issueId}") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@delete
            }
            val issueId = call.parameters["issueId"]?.let { UUID.fromString(it) }
            if (issueId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing issueId")
                return@delete
            }

            val removed = changelogRepository.removeIssue(id, issueId)
            if (removed) {
                call.respond(HttpStatusCode.NoContent, true)
            } else {
                throw NotFoundException("Chart or issue not found")
            }
        }
    }
}
