package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.clients.jsonClient
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.services.ThemePublishService
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
    val multipart = parseMultipartPayload(
        acceptedFormFields = setOf("theme"),
        fileAliases = mapOf(
            "coverArt" to "coverArt",
            "cover" to "coverArt",
            "displayArt" to "displayArt",
        ),
        defaultFilenames = mapOf(
            "coverArt" to "theme-cover-art.png",
            "displayArt" to "theme-display-art.png",
        ),
    )

    if (multipart == null) {
        return ThemeCreatePayload(receive(), ThemeUploadedAssets(coverArt = null, displayArt = null))
    }

    val body = multipart.fields["theme"]
        ?: throw BadRequestException("theme field is required for multipart requests")
    val request = runCatching { jsonClient.decodeFromString<CreateThemeRequest>(body) }
        .getOrElse { throw BadRequestException("Invalid theme JSON payload") }

    return ThemeCreatePayload(
        request,
        ThemeUploadedAssets(
            coverArt = multipart.files["coverArt"],
            displayArt = multipart.files["displayArt"],
        )
    )
}

private suspend fun ApplicationCall.receiveThemeUpdatePayload(): ThemeUpdatePayload {
    val multipart = parseMultipartPayload(
        acceptedFormFields = setOf("theme"),
        fileAliases = mapOf(
            "coverArt" to "coverArt",
            "cover" to "coverArt",
            "displayArt" to "displayArt",
        ),
        defaultFilenames = mapOf(
            "coverArt" to "theme-cover-art.png",
            "displayArt" to "theme-display-art.png",
        ),
    )

    if (multipart == null) {
        return ThemeUpdatePayload(receive(), ThemeUploadedAssets(coverArt = null, displayArt = null))
    }

    val body = multipart.fields["theme"]
        ?: throw BadRequestException("theme field is required for multipart requests")
    val request = runCatching { jsonClient.decodeFromString<UpdateThemeRequest>(body) }
        .getOrElse { throw BadRequestException("Invalid theme JSON payload") }

    return ThemeUpdatePayload(
        request,
        ThemeUploadedAssets(
            coverArt = multipart.files["coverArt"],
            displayArt = multipart.files["displayArt"],
        )
    )
}

fun Route.themeRoutes(
    themeRepository: IThemeRepository,
    userRepository: IUserRepository,
    publishService: ThemePublishService,
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
                    val created = publishService.createAndPublish(
                        uploader = user,
                        request = payload.request,
                        assets = ThemePublishService.Assets(
                            coverArt = payload.assets.coverArt?.toUploadImage(),
                            displayArt = payload.assets.displayArt?.toUploadImage()
                        )
                    )

                    call.respond(HttpStatusCode.Created, created)
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
                    val theme = publishService.updateAndPublish(
                        id = id,
                        userId = userId,
                        request = payload.request,
                        assets = ThemePublishService.Assets(
                            coverArt = payload.assets.coverArt?.toUploadImage(),
                            displayArt = payload.assets.displayArt?.toUploadImage()
                        )
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

                    val success = publishService.deleteAndCleanup(id, userId)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        throw NotFoundException("Theme not found")
                    }
                }
            }
        }
    }
}
