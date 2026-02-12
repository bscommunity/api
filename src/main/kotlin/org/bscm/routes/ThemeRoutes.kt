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
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.plugins.UnauthorizedException
import java.util.*

data class CreateThemeRequest(
    val name: String,
    val replaces: String,
    val coverUrl: String,
    val previewUrl: String
)

data class UpdateThemeRequest(
    val name: String?,
    val replaces: String?,
    val coverUrl: String?,
    val previewUrl: String?
)

private fun ApplicationCall.getUserId(): UUID {
    val principal = principal<JWTPrincipal>()
    return principal?.subject?.let { UUID.fromString(it) } ?: throw UnauthorizedException("User not authenticated")
}

fun Route.themeRoutes(themeRepository: IThemeRepository, ) {
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

                    val theme = themeRepository.getThemeById(id)
                        ?: throw NotFoundException("Theme not found")

                    call.respond(theme)
                }

                /**
                 * Get theme by app content ID.
                 *
                 * Tag: Themes
                 *
                 * Path: contentId [String] App content ID.
                 *
                 * Responses:
                 *   - 400 Missing contentId parameter.
                 *   - 404 Theme not found.
                 *   - 200 Theme details.
                 */
                get("/app/{contentId}") {
                    val contentId = call.parameters["contentId"]
                        ?: throw BadRequestException("Missing contentId parameter")

                    val theme = themeRepository.getAppThemeById(contentId)
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
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val request = call.receive<CreateThemeRequest>()

                    val theme = themeRepository.createTheme(
                        userId = userId,
                        name = request.name,
                        replaces = request.replaces,
                        coverUrl = request.coverUrl,
                        previewUrl = request.previewUrl
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

                    val request = call.receive<UpdateThemeRequest>()

                    val theme = themeRepository.updateTheme(
                        id = id,
                        userId = userId,
                        name = request.name,
                        replaces = request.replaces,
                        coverUrl = request.coverUrl,
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

                    val success = themeRepository.deleteTheme(id, userId)
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
