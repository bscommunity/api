package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.KnownIssue
import org.bscm.models.interfaces.IKnownIssueRepository
import java.util.*

fun Route.knownIssuesRoutes(knownIssueRepository: IKnownIssueRepository) {
    route("/charts") {
        /**
         * Add a known issue to a chart.
         *
         * Tag: Known Issues
         *
         * Path: id [ULong] Chart ID.
         * Body: application/json Issue details to add [KnownIssue].
         *
         * Responses:
         *   - 400 Invalid or missing chart ID.
         *   - 201 Created issue.
         */
        // Add an issue to a chart
        post("{id}/issues") {
            val id = call.parameters["id"]?.toULong()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@post
            }

            val receivedIssue = call.receive<KnownIssue>()

            val createdIssue = knownIssueRepository.addIssue(id, receivedIssue)
            call.respond(HttpStatusCode.Created, createdIssue)
        }

        /**
         * Remove a known issue from a chart.
         *
         * Tag: Known Issues
         *
         * Path: id [ULong] Chart ID.
         * Path: issueId [UUID] Issue ID.
         *
         * Responses:
         *   - 400 Invalid or missing parameters.
         *   - 404 Chart or issue not found.
         *   - 204 Issue removed successfully.
         */
        // Remove an issue from a chart
        delete("{id}/issues/{issueId}") {
            val id = call.parameters["id"]?.toULong()
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
    }
}
