package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import org.bscm.models.dto.chart.BundleDownloadResponse
import org.bscm.models.dto.theme.CreateThemeRequest
import org.bscm.models.dto.theme.UpdateThemeRequest
import org.bscm.models.enums.Visibility
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.plugins.CombinedPrincipal
import org.bscm.plugins.HMACPrincipal
import org.bscm.plugins.UnauthorizedException
import org.bscm.services.BundleDownloadService
import org.bscm.services.ThemePublishService
import org.bscm.services.track.clients.jsonClient
import java.util.*

private val logger = KtorSimpleLogger("ThemeRoutes")

fun Route.themeRoutes(
    themePublishService: ThemePublishService,
    themeRepository: IThemeRepository,
    userRepository: IUserRepository,
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

                    call.respond(
                        if (total != null) Pair(themes, total) else Pair(themes, null)
                    )
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
