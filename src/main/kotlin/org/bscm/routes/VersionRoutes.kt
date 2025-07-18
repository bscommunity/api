package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.repository.ChartRepository
import org.bscm.repository.ContributorRepository
import org.bscm.repository.KnownIssueRepository
import org.bscm.repository.VersionRepository
import org.bscm.services.UploadService
import java.util.*

fun Route.versionRoutes(
    chartRepository: ChartRepository,
    contributorRepository: ContributorRepository,
    knownIssueRepository: KnownIssueRepository,
    versionRepository: VersionRepository,
    uploadService: UploadService,
) {
    route("/charts") {
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

        // Remove a version from a chart (with id)
        delete("versions/{versionId}") {
            val versionId = call.parameters["versionId"]?.let { it.toULong() }

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
