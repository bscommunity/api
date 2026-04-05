package org.bscm.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.bscm.clients.jsonClient
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.services.UploadService
import org.bscm.utils.getUserId
import org.bscm.utils.getUserIdOrNull
import java.util.*

private class ThemeUploadedAssets(
    val coverArt: UploadedImage?,
    val displayArt: UploadedImage?,
)

private data class ThemeCreatePayload(
    val request: CreateThemeRequest,
    val assets: ThemeUploadedAssets,
)

private data class ThemeUpdatePayload(
    val request: UpdateThemeRequest,
    val assets: ThemeUploadedAssets,
)

private suspend fun ApplicationCall.receiveThemeCreatePayload(): ThemeCreatePayload {
    if (!request.contentType().match(ContentType.MultiPart.FormData)) {
        return ThemeCreatePayload(receive(), ThemeUploadedAssets(coverArt = null, displayArt = null))
    }

    val multipart = receiveMultipart()
    var requestJson: String? = null
    var coverArt: UploadedImage? = null
    var displayArt: UploadedImage? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> if (part.name == "theme") requestJson = part.value
            is PartData.FileItem -> if (part.name == "coverArt" || part.name == "cover") {
                val bytes = part.provider().toByteArray()
                if (bytes.isNotEmpty()) {
                    coverArt = UploadedImage(
                        bytes = bytes,
                        filename = part.originalFileName ?: "theme-cover-art.png",
                        contentType = part.contentType ?: ContentType.Application.OctetStream,
                    )
                }
            } else if (part.name == "displayArt") {
                val bytes = part.provider().toByteArray()
                if (bytes.isNotEmpty()) {
                    displayArt = UploadedImage(
                        bytes = bytes,
                        filename = part.originalFileName ?: "theme-display-art.png",
                        contentType = part.contentType ?: ContentType.Application.OctetStream,
                    )
                }
            }
            else -> {}
        }
        part.dispose()
    }

    val body = requestJson ?: throw BadRequestException("theme field is required for multipart requests")
    val request = runCatching { jsonClient.decodeFromString<CreateThemeRequest>(body) }
        .getOrElse { throw BadRequestException("Invalid theme JSON payload") }

    return ThemeCreatePayload(request, ThemeUploadedAssets(coverArt = coverArt, displayArt = displayArt))
}

private suspend fun ApplicationCall.receiveThemeUpdatePayload(): ThemeUpdatePayload {
    if (!request.contentType().match(ContentType.MultiPart.FormData)) {
        return ThemeUpdatePayload(receive(), ThemeUploadedAssets(coverArt = null, displayArt = null))
    }

    val multipart = receiveMultipart()
    var requestJson: String? = null
    var coverArt: UploadedImage? = null
    var displayArt: UploadedImage? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> if (part.name == "theme") requestJson = part.value
            is PartData.FileItem -> if (part.name == "coverArt" || part.name == "cover") {
                val bytes = part.provider().toByteArray()
                if (bytes.isNotEmpty()) {
                    coverArt = UploadedImage(
                        bytes = bytes,
                        filename = part.originalFileName ?: "theme-cover-art.png",
                        contentType = part.contentType ?: ContentType.Application.OctetStream,
                    )
                }
            } else if (part.name == "displayArt") {
                val bytes = part.provider().toByteArray()
                if (bytes.isNotEmpty()) {
                    displayArt = UploadedImage(
                        bytes = bytes,
                        filename = part.originalFileName ?: "theme-display-art.png",
                        contentType = part.contentType ?: ContentType.Application.OctetStream,
                    )
                }
            }
            else -> {}
        }
        part.dispose()
    }

    val body = requestJson ?: throw BadRequestException("theme field is required for multipart requests")
    val request = runCatching { jsonClient.decodeFromString<UpdateThemeRequest>(body) }
        .getOrElse { throw BadRequestException("Invalid theme JSON payload") }

    return ThemeUpdatePayload(request, ThemeUploadedAssets(coverArt = coverArt, displayArt = displayArt))
}

fun Route.themeRoutes(
    themeRepository: IThemeRepository,
    activityRepository: IActivityRepository,
    uploadService: UploadService,
    userRepository: IUserRepository,
) {
    route("/themes") {
        authenticate("auth-bearer", optional = true) {
            rateLimit(RateLimitName("unrestricted")) {
                /**
                 * List themes with optional search and filtering.
                 *
                 * Tag: Themes
                 *
                 * Query: search [String] Optional search string to filter themes.
                 * Query: limit [Integer] Optional limit for results.
                 * Query: offset [Integer] Optional pagination offset.
                 * Query: ids [String] Comma-separated theme IDs to retrieve.
                 *
                 * Response: 200 application/json List of themes.
                 */
                get {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("sub")?.asString()?.let { UUID.fromString(it) }

                    val search = call.request.queryParameters["search"]
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    val ids = call.request.queryParameters.getAll("ids")

                    val themes = themeRepository.getThemes(
                        userId = userId,
                        contentIds = ids,
                        search = search,
                        limit = limit,
                        offset = offset
                    )

                    call.respond(themes)
                }

                /**
                 * Get theme by ID.
                 *
                 * Tag: Themes
                 *
                 * Path: id [ULong] Theme ID.
                 *
                 * Responses:
                 *   - 400 ID parameter is malformatted or missing.
                 *   - 404 Theme not found.
                 *   - 200 Theme details.
                 */
                get("/{id}") {
                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing ID parameter")

                    val theme = themeRepository.getThemeById(id, call.getUserIdOrNull())
                        ?: throw NotFoundException("Theme not found")

                    call.respond(theme)
                }

                /**
                 * Get theme by content ID.
                 *
                 * Tag: Themes
                 *
                 * Path: contentId [String] Content ID.
                 *
                 * Responses:
                 *   - 400 Missing contentId parameter.
                 *   - 404 Theme not found.
                 *   - 200 Theme details.
                 */
                get("/content/{id}") {
                    val contentId = call.parameters["id"]
                        ?: throw BadRequestException("Missing contentId parameter")

                    val theme = themeRepository.getAppThemeById(contentId, call.getUserIdOrNull())
                        ?: throw NotFoundException("Theme not found")

                    call.respond(theme)
                }
            }
        }

        authenticate("auth-bearer") {
            rateLimit(RateLimitName("restricted")) {
                /**
                 * Create a new theme.
                 *
                 * Tag: Themes
                 *
                 * Security: auth-bearer
                 *
                 * Body: application/json Theme name, replacement target, and URLs [CreateThemeRequest].
                 *
                 * Response: 201 application/json Created theme.
                 * Response: 400 application/json Authentication required or invalid request.
                 */
                post {
                    val userId = call.getUserId()
                    val user = userRepository.getUserById(userId)
                        ?: throw NotFoundException("User not found")

                    val payload = call.receiveThemeCreatePayload()
                    val request = payload.request
                    val coverArtInput = payload.assets.coverArt
                    val displayArtInput = payload.assets.displayArt

                    if (coverArtInput == null && request.coverUrl.isNullOrBlank()) {
                        throw BadRequestException("coverUrl or coverArt file is required")
                    }
                    if (displayArtInput == null && request.displayArtUrl.isNullOrBlank()) {
                        throw BadRequestException("displayArtUrl or displayArt file is required")
                    }

                    val discordResponse = uploadService.uploadTheme(
                        UploadService.ThemePublishData(
                            title = request.name,
                            description = request.description,
                            uploader = user,
                            replaces = request.replaces,
                            trailerUrl = request.previewUrl,
                            coverArtUrl = request.coverUrl,
                            coverArt = coverArtInput?.let {
                                UploadService.UploadImage(it.bytes, it.filename, it.contentType)
                            },
                            displayArtUrl = request.displayArtUrl,
                            displayArt = displayArtInput?.let {
                                UploadService.UploadImage(it.bytes, it.filename, it.contentType)
                            },
                            trackUrls = emptyList(),
                        )
                    )

                    val resolvedDisplayArtUrl = discordResponse.attachments
                        .firstOrNull { it.filename.contains("display-art", ignoreCase = true) }
                        ?.url
                        ?: discordResponse.embeds.firstOrNull()?.image?.url
                        ?: request.displayArtUrl
                        ?: throw IllegalStateException("Unable to resolve displayArt URL from Discord response")

                    val resolvedCoverUrl = discordResponse.attachments
                        .firstOrNull { it.filename.contains("cover-art", ignoreCase = true) }
                        ?.url
                        ?: discordResponse.embeds.firstOrNull()?.thumbnail?.url
                        ?: request.coverUrl
                        ?: throw IllegalStateException("Unable to resolve cover URL from Discord response")

                    val theme = themeRepository.createTheme(
                        userId = userId,
                        name = request.name,
                        replaces = request.replaces,
                        coverUrl = resolvedCoverUrl,
                        displayArtUrl = resolvedDisplayArtUrl,
                        previewUrl = request.previewUrl,
                        id = discordResponse.id.toULong(),
                    )

                    activityRepository.logActivity(
                        userId = userId,
                        type = ActivityType.CREATED_THEME,
                        targetId = theme.contentId
                    )

                    call.respond(HttpStatusCode.Created, theme)
                }

                /**
                 * Update an existing theme.
                 *
                 * Tag: Themes
                 *
                 * Path: id [ULong] Theme ID.
                 * Body: application/json Fields to update [UpdateThemeRequest].
                 *
                 * Responses:
                 *   - 400 ID parameter is malformatted or missing.
                 *   - 200 Updated theme.
                 */
                put("/{id}") {
                    val userId = call.getUserId()

                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing ID parameter")

                    val payload = call.receiveThemeUpdatePayload()
                    val request = payload.request
                    val resolvedCoverUrl = payload.assets.coverArt?.let {
                        uploadService.uploadCoverImage(
                            coverBytes = it.bytes,
                            filename = it.filename,
                            contentType = it.contentType,
                            context = "theme-update:$id"
                        )
                    } ?: request.coverUrl

                    val resolvedDisplayArtUrl = payload.assets.displayArt?.let {
                        uploadService.uploadCoverImage(
                            coverBytes = it.bytes,
                            filename = it.filename,
                            contentType = it.contentType,
                            context = "theme-display-art-update:$id"
                        )
                    } ?: request.displayArtUrl

                    val theme = themeRepository.updateTheme(
                        id = id,
                        userId = userId,
                        name = request.name,
                        replaces = request.replaces,
                        coverUrl = resolvedCoverUrl,
                        displayArtUrl = resolvedDisplayArtUrl,
                        previewUrl = request.previewUrl
                    )

                    call.respond(theme)
                }

                /**
                 * Delete a theme.
                 *
                 * Tag: Themes
                 *
                 * Path: id [ULong] Theme ID.
                 *
                 * Responses:
                 *   - 400 ID parameter is malformatted or missing.
                 *   - 404 Theme not found.
                 *   - 204 Theme deleted successfully.
                 */
                delete("/{id}") {
                    val userId = call.getUserId()

                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing ID parameter")

                    val contentId = themeRepository.getThemeById(id, userId)?.contentId
                        ?: throw NotFoundException("Theme not found")

                    val success = themeRepository.deleteTheme(id, userId)
                    if (success) {
                        activityRepository.removeActivityByTypeAndTarget(
                            type = ActivityType.CREATED_THEME,
                            targetId = contentId
                        )
                        runCatching { uploadService.deleteMessage(id.toString()) }
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        throw NotFoundException("Theme not found")
                    }
                }
            }
        }
    }
}
