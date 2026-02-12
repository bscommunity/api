package org.bscm.routes

import io.klogging.noCoLogger
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
import org.bscm.clients.jsonClient
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.plugins.UnauthorizedException
import org.bscm.services.UploadService
import org.bscm.utils.QueryUtils.getNormalizedQuery
import org.bscm.utils.QueryUtils.similarity
import java.util.*

fun Route.versionRoutes(
    versionRepository: IVersionRepository,
    chartRepository: IChartRepository,
    userRepository: IUserRepository,
    uploadService: UploadService,
    supportUploadService: UploadService
) {
    val logger = noCoLogger("VersionRoutes")

    // Routes that require JWT authentication only (dashboard operations)
    authenticate("auth-bearer") {
        rateLimit(RateLimitName("restricted")) {
            route("/charts") {
                /**
                 * Add new version to a chart.
                 *
                 * Tag: Versions
                 *
                 * Path: chartId [ULong] Chart ID.
                 * Body: multipart/form-data Version JSON and chart bundle file.
                 *   - version: Version metadata as JSON (CreateVersionRequest).
                 *   - bundle: Chart bundle file (max 10MB).
                 *
                 * Responses:
                 *   - 400 Invalid chart ID or missing version/bundle data.
                 *   - 401 User not authenticated or not authorized.
                 *   - 404 Chart not found.
                 *   - 201 Created version.
                 */
                post("{chartId}/versions") {
                    val chartId = call.parameters["chartId"]?.toULong()
                        ?: throw BadRequestException("Invalid or missing chart ID")

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.subject?.let { UUID.fromString(it) }
                        ?: throw UnauthorizedException("User unauthorized")

                    val user = userRepository.getUserById(userId) ?: throw UnauthorizedException("User not found")

                    logger.info("Received request to add version to chart with ID: $chartId")

                    val chart = chartRepository.getChartById(chartId) ?: throw NotFoundException("Chart not found")

                    if (chart.latestVersion == null) {
                        throw Exception("Chart does not have a latest version")
                    }

                    // ...existing code...
                    val multipart = call.receiveMultipart()
                    var versionJson: String? = null
                    var bundleFileBytes: ByteArray? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                if (part.name == "version") {
                                    versionJson = part.value
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

                    if (versionJson == null || bundleFileBytes == null) {
                        throw BadRequestException("Version data or bundle missing")
                    }

                    // Validate the bundle file size
                    if (bundleFileBytes.size > 10 * 1024 * 1024) {
                        throw BadRequestException("Bundle file size exceeds 10MB limit")
                    }

                    // Deserialize the chart JSON
                    val createRequest = try {
                        jsonClient.decodeFromString<CreateVersionRequest>(versionJson)
                    } catch (e: Exception) {
                        throw BadRequestException("Invalid chart JSON: ${e.message}")
                    }

                    // Validate the create request
                    val trackSimilarity = similarity(
                        getNormalizedQuery(createRequest.track),
                        getNormalizedQuery(chart.track)
                    )
                    val artistSimilarity = similarity(
                        getNormalizedQuery(createRequest.artist),
                        getNormalizedQuery(chart.artist)
                    )

                    if (trackSimilarity < 0.3 || artistSimilarity < 0.3) throw BadRequestException("Track or artist does not match the chart")

                    logger.info("Creating version with request: $createRequest")

                    // Upload the chart bundle
                    val discordResponse = uploadService.uploadVersion(
                        chart.id,
                        chart,
                        createRequest,
                        user,
                        bundleFileBytes
                    )
                    logger.info("Successfully uploaded bundle with ${discordResponse.id}")

                    val attachment = discordResponse.attachments.lastOrNull()

                    if (attachment == null) {
                        call.respond(HttpStatusCode.BadRequest, "Failed to upload bundle")
                        return@post
                    }

                    val createRequestWithUrl = createRequest.copy(
                        id = attachment.id.toULong(),
                        bundleUrl = attachment.url
                    )

                    // Create the version in the repository
                    val createdVersion = versionRepository.addVersion(chart, createRequestWithUrl)

                    call.respond(HttpStatusCode.Created, createdVersion)
                }

                /**
                 * Delete a version from a chart.
                 *
                 * Tag: Versions
                 *
                 * Path: versionId [ULong] Version ID.
                 *
                 * Responses:
                 *   - 400 Invalid or missing version ID.
                 *   - 401 User not authenticated.
                 *   - 404 Version or chart not found.
                 *   - 204 Version deleted successfully.
                 */
                // Remove a version from a chart (with id)
                delete("versions/{versionId}") {
                    val versionId = call.parameters["versionId"]

                    if (versionId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                        return@delete
                    }

                    val versionIdULong = versionId.toULong()

                    val version =
                        versionRepository.getVersionById(versionIdULong) ?: throw NotFoundException("Version not found")
                    val chart =
                        chartRepository.getChartById(version.chartId.toULong())
                            ?: throw NotFoundException("Chart not found")

                    versionRepository.removeVersion(chart.latestVersion!!, versionIdULong)

                    logger.info("Removing version with ID: $versionIdULong from chart with ID: ${chart.id}")

                    // Delete the version bundle from Discord
                    uploadService.deleteVersion(
                        chart.id,
                        chart.track,
                        chart.versions,
                        versionId
                    )

                    call.respond(HttpStatusCode.NoContent, "Version removed successfully")
                }
            }
        }
    }

    /**
     * Refresh bundle URLs for all charts' latest versions.
     *
     * Tag: Versions
     *
     * Query: verify [String] Verification token (must match JWT secret).
     *
     * Responses:
     *   - 401 Invalid verification token.
     *   - 500 Failed to refresh bundle URLs.
     *   - 200 Success message with count of refreshed URLs.
     */
    // Refreshes bundle URLs for all charts latest versions
    route("/refresh") {
        post {
            val verify = call.request.queryParameters["verify"]

            if (verify != application.environment.config.property("jwt.secret").getString()) {
                throw UnauthorizedException("Invalid verification token")
            }

            val messages = uploadService.refreshBundleUrls()

            if (messages.isEmpty()) {
                throw Exception("Failed to refresh bundle")
            }

            // Fetch audio URLs from support channel
            // val audioUrls = supportUploadService.refreshAudioUrls()
            // println("Found ${audioUrls.size} audio URLs from support channel")

            val result = chartRepository.refreshChartsBundles(messages)

            if (result) {
                call.respond(HttpStatusCode.OK, "Successfully refreshed ${messages.size} bundle URLs")
            } else {
                throw Exception("Failed to refresh bundle URLs")
            }
        }
    }
}
