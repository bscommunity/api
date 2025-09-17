package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.plugins.UnauthorizedException
import org.bscm.repository.UserInteractionRepository
import java.util.*

data class InteractionRequest(
    val contentType: String,
    val contentId: String
)

fun Route.userInteractionRoutes(
    userInteractionRepository: UserInteractionRepository
) {
    route("/interactions") {
        authenticate("auth-jwt") {
            rateLimit(RateLimitName("restricted")) {
                post("/like") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val request = call.receive<InteractionRequest>()
                    val contentType = ContentType.valueOf(request.contentType.uppercase())
                    val contentId = request.contentId.toULongOrNull()
                        ?: throw BadRequestException("Invalid content ID")

                    val success = userInteractionRepository.likeContent(userId, contentType, contentId)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Content liked successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to like content"))
                    }
                }

                delete("/like") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val request = call.receive<InteractionRequest>()
                    val contentType = ContentType.valueOf(request.contentType.uppercase())
                    val contentId = request.contentId.toULongOrNull()
                        ?: throw BadRequestException("Invalid content ID")

                    val success = userInteractionRepository.unlikeContent(userId, contentType, contentId)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Content unliked successfully"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Like not found"))
                    }
                }

                post("/favorite") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val request = call.receive<InteractionRequest>()
                    val contentType = ContentType.valueOf(request.contentType.uppercase())
                    val contentId = request.contentId.toULongOrNull()
                        ?: throw BadRequestException("Invalid content ID")

                    val success = userInteractionRepository.favoriteContent(userId, contentType, contentId)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Content favorited successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to favorite content"))
                    }
                }

                delete("/favorite") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val request = call.receive<InteractionRequest>()
                    val contentType = ContentType.valueOf(request.contentType.uppercase())
                    val contentId = request.contentId.toULongOrNull()
                        ?: throw BadRequestException("Invalid content ID")

                    val success = userInteractionRepository.unfavoriteContent(userId, contentType, contentId)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Content unfavorited successfully"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Favorite not found"))
                    }
                }

                get("/status/{contentType}/{contentId}") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val contentType = ContentType.valueOf(call.parameters["contentType"]!!.uppercase())
                    val contentId = call.parameters["contentId"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid content ID")

                    val interaction = userInteractionRepository.getUserInteraction(userId, contentType, contentId)
                    val stats = userInteractionRepository.getContentInteractionStats(contentType, contentId)

                    call.respond(mapOf(
                        "isLiked" to (interaction?.likedAt != null),
                        "isFavorited" to (interaction?.favoritedAt != null),
                        "stats" to stats
                    ))
                }

                get("/liked/{contentType}") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val contentType = ContentType.valueOf(call.parameters["contentType"]!!.uppercase())
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()

                    val likedContent = userInteractionRepository.getUserLikedContent(userId, contentType, limit, offset)
                    call.respond(likedContent)
                }

                get("/favorited/{contentType}") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val contentType = ContentType.valueOf(call.parameters["contentType"]!!.uppercase())
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()

                    val favoritedContent = userInteractionRepository.getUserFavoritedContent(userId, contentType, limit, offset)
                    call.respond(favoritedContent)
                }
            }
        }
    }
}
