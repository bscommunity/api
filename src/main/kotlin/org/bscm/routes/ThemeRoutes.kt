package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.plugins.UnauthorizedException
import org.bscm.services.ThemePublishService
import org.bscm.services.track.clients.jsonClient
import java.util.*

private val logger = KtorSimpleLogger("ThemeRoutes")

fun Route.themeRoutes(
    themePublishService: ThemePublishService,
    themeRepository: IThemeRepository,
    userRepository: IUserRepository,
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

                    call.respond(
                        if (total != null) Pair(themes, total) else Pair(themes, null)
                    )
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
                        fileAliases = mapOf("cover" to "coverArt", "display" to "displayArt"),
                    ) ?: throw BadRequestException("multipart/form-data is required")

                    val themeJson = multipart.fields["theme"]
                        ?: throw BadRequestException("theme JSON field is required")

                    val request = runCatching {
                        jsonClient.decodeFromString<CreateThemeRequest>(themeJson)
                    }.getOrElse {
                        throw BadRequestException("Invalid theme JSON: ${it.message}")
                    }

                    val theme = themePublishService.createAndPublish(
                        uploader = user,
                        request = request,
                        assets = ThemePublishService.Assets(
                            coverArtBytes = multipart.files["cover"]?.bytes,
                            coverArtContentType = multipart.files["cover"]?.contentType,
                            displayArtBytes = multipart.files["display"]?.bytes,
                            displayArtContentType = multipart.files["display"]?.contentType,
                        )
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
                        fileAliases = mapOf("cover" to "coverArt", "display" to "displayArt"),
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
            }
        }
    }
}
