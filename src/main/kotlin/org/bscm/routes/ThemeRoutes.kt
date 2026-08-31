package org.bscm.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import org.bscm.models.dto.chart.BundleDownloadResponse
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.dto.user.PagedResponse
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.dto.version.VersionBundleData
import org.bscm.models.enums.Visibility
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.plugins.CombinedPrincipal
import org.bscm.plugins.HMACPrincipal
import org.bscm.plugins.UnauthorizedException
import org.bscm.services.BundleDownloadService
import org.bscm.services.UploadService
import org.bscm.services.publish.ThemePublishService
import org.bscm.services.track.clients.jsonClient
import org.bscm.utils.getUserIdOrNull
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.security.MessageDigest
import java.util.*

private val logger = KtorSimpleLogger("ThemeRoutes")

fun Route.themeRoutes(
    themePublishService: ThemePublishService,
    themeRepository: IThemeRepository,
    userRepository: IUserRepository,
    versionRepository: IVersionRepository,
    uploadService: UploadService,
    bundleDownloadService: BundleDownloadService,
) {
    route("/themes") {

        authenticate("auth-public") {
            rateLimit(RateLimitName("restricted")) {
                get {
                    val search = call.request.queryParameters["query"]
                        ?.takeIf { it.isNotBlank() }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    val count = call.request.queryParameters["count"]?.toBoolean() ?: false

                    val themes = themeRepository.getThemes(
                        search = search,
                        limit = limit,
                        offset = offset
                    )

                    val total = if (count) {
                        themeRepository.countThemes(search = search)
                    } else null

                    call.respond(PagedResponse(items = themes, total = total))
                }

                get("{id}") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing theme ID")

                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    if (jwtPrincipal == null && hmacPrincipal == null && combinedPrincipal == null) {
                        throw UnauthorizedException("Unauthorized")
                    }

                    val requesterId = when {
                        combinedPrincipal != null ->
                            runCatching { UUID.fromString(combinedPrincipal.jwtPrincipal.subject) }.getOrNull()
                        jwtPrincipal != null ->
                            jwtPrincipal.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        else -> null
                    }

                    val theme = themeRepository.getThemeById(id, userId = requesterId)
                        ?: throw NotFoundException("Theme not found")

                    if (theme.visibility != Visibility.PUBLIC) {
                        val isContributor = requesterId != null &&
                                theme.contributors.any { contributor -> contributor.user.id == requesterId }
                        if (!isContributor) throw NotFoundException("Theme not found")
                    }

                    call.respond(theme)
                }

                get("{id}/bundle") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing theme ID")

                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    if (jwtPrincipal == null && hmacPrincipal == null && combinedPrincipal == null) {
                        throw UnauthorizedException("Unauthorized")
                    }

                    val requesterId = when {
                        combinedPrincipal != null ->
                            runCatching { UUID.fromString(combinedPrincipal.jwtPrincipal.subject) }.getOrNull()
                        jwtPrincipal != null ->
                            jwtPrincipal.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        else -> null
                    }

                    val theme = themeRepository.getThemeById(id, userId = requesterId)
                        ?: throw NotFoundException("Theme not found")

                    if (theme.visibility != Visibility.PUBLIC) {
                        val isContributor = requesterId != null &&
                                theme.contributors.any { contributor -> contributor.user.id == requesterId }
                        if (!isContributor) throw NotFoundException("Theme not found")
                    }

                    val url = bundleDownloadService.resolveBundleUrl(
                        catalogItemId = theme.id,
                        messageId = theme.discordMessageId
                            ?: throw IllegalStateException("Theme ${theme.id} has no Discord message ID"),
                    )

                    call.respond(BundleDownloadResponse(url = url))
                }

                get("{id}/bundle/file") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing theme ID")

                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    if (jwtPrincipal == null && hmacPrincipal == null && combinedPrincipal == null) {
                        throw UnauthorizedException("Unauthorized")
                    }

                    val requesterId = when {
                        combinedPrincipal != null ->
                            runCatching { UUID.fromString(combinedPrincipal.jwtPrincipal.subject) }.getOrNull()
                        jwtPrincipal != null ->
                            jwtPrincipal.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        else -> null
                    }

                    val theme = themeRepository.getThemeById(id, userId = requesterId)
                        ?: throw NotFoundException("Theme not found")

                    if (theme.visibility != Visibility.PUBLIC) {
                        val isContributor = requesterId != null &&
                                theme.contributors.any { contributor -> contributor.user.id == requesterId }
                        if (!isContributor) throw NotFoundException("Theme not found")
                    }

                    val bytes = bundleDownloadService.fetchBundleBytes(
                        catalogItemId = theme.id,
                        messageId = theme.discordMessageId
                            ?: throw IllegalStateException("Theme ${theme.id} has no Discord message ID"),
                    )

                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"theme_${theme.id}.zip\"",
                    )
                    call.respondBytes(bytes, ContentType.Application.Zip)
                }

                /**
                 * Returns all versions for a theme, sorted by version code ascending.
                 *
                 * Tag: Versions
                 *
                 * Path: id [String] Theme catalog item ID.
                 *
                 * Responses:
                 *   - 200 application/json [Array] List of theme versions.
                 *   - 400 application/json [Error] Invalid or missing ID parameter.
                 *   - 401 application/json [Error] Unauthorized access.
                 *   - 404 application/json [Error] Theme not found.
                 */
                get("{id}/versions") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing theme ID")

                    val jwtPrincipal = call.principal<JWTPrincipal>()
                    val hmacPrincipal = call.principal<HMACPrincipal>()
                    val combinedPrincipal = call.principal<CombinedPrincipal>()

                    if (jwtPrincipal == null && hmacPrincipal == null && combinedPrincipal == null) {
                        throw UnauthorizedException("Unauthorized")
                    }

                    val requesterId = when {
                        combinedPrincipal != null ->
                            runCatching { UUID.fromString(combinedPrincipal.jwtPrincipal.subject) }.getOrNull()
                        jwtPrincipal != null ->
                            jwtPrincipal.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        else -> null
                    }

                    val theme = themeRepository.getThemeById(id, userId = requesterId)
                        ?: throw NotFoundException("Theme not found")

                    if (theme.visibility != Visibility.PUBLIC) {
                        val isContributor = requesterId != null &&
                                theme.contributors.any { contributor -> contributor.user.id == requesterId }
                        if (!isContributor) throw NotFoundException("Theme not found")
                    }

                    val versions = versionRepository.getVersions(theme.id)
                    call.respond(versions)
                }
            }
        }

        authenticate("auth-bearer") {
            rateLimit(RateLimitName("restricted")) {

                post {
                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    val user = userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    val multipart = call.parseMultipartPayload(
                        acceptedFormFields = setOf("theme"),
                        fileAliases = mapOf("cover" to "cover", "display" to "display", "bundle" to "bundle"),
                    ) ?: throw BadRequestException("multipart/form-data is required")

                    val themeJson = multipart.fields["theme"]
                        ?: throw BadRequestException("theme JSON field is required")

                    val request = runCatching {
                        jsonClient.decodeFromString<CreateThemeRequest>(themeJson)
                    }.getOrElse {
                        throw BadRequestException("Invalid theme JSON: ${it.message}")
                    }

                    val bundleFileBytes = multipart.files["bundle"]?.bytes
                    val publishSessionId = call.request.headers["X-Publish-Session-Id"]

                    val theme = themePublishService.createAndPublish(
                        uploader = user,
                        request = request,
                        assets = ThemePublishService.Assets(
                            coverArtBytes = multipart.files["cover"]?.bytes,
                            coverArtContentType = multipart.files["cover"]?.contentType,
                            displayArtBytes = multipart.files["display"]?.bytes,
                            displayArtContentType = multipart.files["display"]?.contentType,
                            bundleBytes = bundleFileBytes,
                        ),
                        publishSessionId = publishSessionId,
                    )

                    logger.info("Theme ${theme.id} created by user $userId")
                    call.respond(HttpStatusCode.Created, theme)
                }

                put("{id}") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing theme ID")

                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    val multipart = call.parseMultipartPayload(
                        acceptedFormFields = setOf("theme"),
                        fileAliases = mapOf("cover" to "cover", "display" to "display"),
                    ) ?: throw BadRequestException("multipart/form-data is required")

                    val themeJson = multipart.fields["theme"]
                        ?: throw BadRequestException("theme JSON field is required")

                    val request = runCatching {
                        jsonClient.decodeFromString<UpdateThemeRequest>(themeJson)
                    }.getOrElse {
                        throw BadRequestException("Invalid theme JSON: ${it.message}")
                    }

                    val theme = themePublishService.updateAndPublish(
                        id = id,
                        userId = userId,
                        request = request,
                        assets = ThemePublishService.Assets(
                            coverArtBytes = multipart.files["cover"]?.bytes,
                            coverArtContentType = multipart.files["cover"]?.contentType,
                            displayArtBytes = multipart.files["display"]?.bytes,
                            displayArtContentType = multipart.files["display"]?.contentType,
                            bundleBytes = null,
                        )
                    )

                    logger.info("Theme $id updated by user $userId")
                    call.respond(theme)
                }

                delete("{id}") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing theme ID")

                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    val deleted = themePublishService.deleteAndCleanup(id, userId)
                    if (!deleted) {
                        throw NotFoundException("Theme not found or not owned by user")
                    }

                    logger.info("Theme $id deleted by user $userId")
                    call.respond(HttpStatusCode.NoContent)
                }

                post("{id}/versions") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing theme ID")

                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    val user = userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    val theme = themeRepository.getThemeById(id, userId = userId)
                        ?: throw NotFoundException("Theme not found")

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

                    val bundleHash = MessageDigest.getInstance("SHA-256")
                        .digest(bundleFileBytes)
                        .joinToString("") { "%02x".format(it) }

                    val createRequest = try {
                        jsonClient.decodeFromString<CreateVersionRequest>(versionJson)
                    } catch (e: Exception) {
                        throw BadRequestException("Invalid version JSON: ${e.message}")
                    }

                    logger.info("Creating version for theme $id")

                    val existingVersions = versionRepository.getVersions(theme.id)

                    val discordResponse = uploadService.uploadThemeVersion(
                        theme = theme,
                        author = user,
                        themeBundle = bundleFileBytes,
                        existingVersions = existingVersions,
                    )

                    val attachment = discordResponse.attachments.lastOrNull()
                        ?: throw BadRequestException("Discord returned no attachment after upload")

                    val createdVersion = suspendTransaction {
                        versionRepository.addVersion(
                            theme.id,
                            VersionBundleData(
                                id = attachment.id.toULong(),
                                fileSizeBytes = bundleFileBytes.size.toLong(),
                                changelog = createRequest.changelog,
                            ),
                            bundleHash,
                        )
                    }

                    logger.info("Version ${createdVersion.id} (v${createdVersion.versionCode}) created for theme $id")

                    call.respond(HttpStatusCode.Created, createdVersion)
                }

                delete("{id}/versions/{versionId}") {
                    val catalogItemId = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing theme ID")

                    val versionId = call.parameters["versionId"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing version ID")

                    val version = versionRepository.getVersionById(versionId)
                        ?: throw NotFoundException("Version not found")

                    val theme = themeRepository.getThemeById(catalogItemId, userId = call.getUserIdOrNull())
                        ?: throw NotFoundException("Theme not found")

                    logger.info("Removing version $versionId from theme ${theme.id}")

                    versionRepository.removeVersion(versionId)

                    val versions = versionRepository.getVersions(theme.id)

                    uploadService.deleteThemeVersion(
                        theme = theme,
                        versions = versions,
                        versionId = versionId.toString()
                    )

                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
