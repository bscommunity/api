package org.bscm.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.openapi.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.plugins.UnauthorizedException
import org.bscm.repository.ChartRepository
import org.bscm.services.UploadService
import org.bscm.services.track.clients.jsonClient
import org.bscm.utils.QueryUtils.getNormalizedQuery
import org.bscm.utils.QueryUtils.similarity
import org.bscm.utils.getUserIdOrNull
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

private val logger = KtorSimpleLogger("VersionRoutes")

@OptIn(ExperimentalKtorApi::class)
fun Route.versionRoutes(
    versionRepository: IVersionRepository,
    chartRepository: IChartRepository,
    userRepository: IUserRepository,
    uploadService: UploadService,
    // supportUploadService: UploadService
) {

    authenticate("auth-bearer") {
        rateLimit(RateLimitName("restricted")) {
            route("/versions/chart") {
                post("{chartId}") {
                    val chartId = call.parameters["chartId"]
                        ?: throw BadRequestException("Invalid or missing chart ID")

                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    // UUID.fromString() throws on malformed subjects.
                    // runCatching wraps it so a bad JWT subject returns 401, not 500.

                    val user = userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    val chart = chartRepository.getChartById(
                        chartId,
                        ChartRepository.ChartAddons(versions = true),
                        requestingUserId = userId,
                    ) ?: throw NotFoundException("Chart not found")

                    logger.info("Received request to add version to chart $chartId")

                    // --- Multipart parsing ---

                    val multipart = call.receiveMultipart()
                    var versionJson: String? = null
                    var bundleFileBytes: ByteArray? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> if (part.name == "version") versionJson = part.value
                            is PartData.FileItem -> if (part.name == "bundle") bundleFileBytes = part.provider().toByteArray()
                            else -> {}
                        }
                        part.dispose()
                    }

                    if (versionJson == null || bundleFileBytes == null) {
                        throw BadRequestException("Version data or bundle missing")
                    }

                    if (bundleFileBytes.size > 10 * 1024 * 1024) {
                        throw BadRequestException("Bundle file size exceeds 10MB limit")
                    }

                    val createRequest = try {
                        jsonClient.decodeFromString<CreateVersionRequest>(versionJson)
                    } catch (e: Exception) {
                        throw BadRequestException("Invalid version JSON: ${e.message}")
                    }

                    // --- Similarity validation ---

                    val trackSimilarity = similarity(
                        getNormalizedQuery(createRequest.track),
                        getNormalizedQuery(chart.track.title)
                    )
                    val artistSimilarity = similarity(
                        getNormalizedQuery(createRequest.artist),
                        getNormalizedQuery(chart.track.artist)
                    )

                    if (trackSimilarity < 0.3 || artistSimilarity < 0.3) {
                        throw BadRequestException("Track or artist does not match the chart")
                    }

                    logger.info("Creating version for chart $chartId: $createRequest")

                    val existingVersions = versionRepository.getVersions(chart.id)

                    val discordResponse = uploadService.uploadVersion(
                        chart = chart,
                        version = createRequest.copy(fileSizeBytes = bundleFileBytes.size.toLong()),
                        author = user,
                        chartBundle = bundleFileBytes,
                        existingVersions = existingVersions,
                    )

                    val attachment = discordResponse.attachments.lastOrNull()
                        ?: throw BadRequestException("Discord returned no attachment after upload")

                    val createRequestWithUrl = createRequest.copy(
                        id = attachment.id.toULong(),
                        bundleUrl = attachment.url
                    )

                    val createdVersion = suspendTransaction {
                        versionRepository.addVersion(chart.id, createRequestWithUrl)
                    }

                    logger.info("Version ${createdVersion.id} (v${createdVersion.versionCode}) created for chart $chartId")

                    call.respond(HttpStatusCode.Created, createdVersion)

                }.describe {
                    tag("Versions")
                    summary = "Add new version to a chart."
                    requestBody {
                        description = "Chart bundle upload with version metadata"
                        required = true
                        ContentType.MultiPart.FormData {
                            schema = JsonSchema(
                                type = JsonType.OBJECT,
                                properties = mapOf(
                                    "bundle" to ReferenceOr.Value(
                                        JsonSchema(
                                            type = JsonType.STRING,
                                            format = "binary",
                                            description = "The chart bundle file to upload"
                                        )
                                    ),
                                    "version" to ReferenceOr.Value(
                                        JsonSchema(
                                            type = JsonType.STRING,
                                            description = "JSON string containing CreateVersionRequest"
                                        )
                                    )
                                ),
                                required = listOf("bundle", "version")
                            )
                        }
                    }
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
                delete("{versionId}") {
                    val versionId = call.parameters["versionId"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing version ID")

                    val version = versionRepository.getVersionById(versionId)
                        ?: throw NotFoundException("Version not found")

                    val chart = chartRepository.getChartById(
                        version.catalogItemId,
                        ChartRepository.ChartAddons(versions = false),
                        requestingUserId = call.getUserIdOrNull(),
                    ) ?: throw NotFoundException("Chart not found")

                    logger.info("Removing version $versionId from chart ${chart.id}")

                    versionRepository.removeVersion(versionId, chart.latestVersion?.id, chart.versionsCount)

                    val versions = versionRepository.getVersions(chart.id)

                    uploadService.deleteVersion(
                        chart = chart,
                        versions = versions,
                        versionId = versionId.toString()
                    )

                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}