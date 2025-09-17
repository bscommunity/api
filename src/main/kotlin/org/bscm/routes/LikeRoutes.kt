package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bscm.models.enums.ContentType
import org.bscm.services.CollectionService
import java.util.*

@Serializable
data class LikeRequest(
    val contentType: ContentType,
    val contentId: ULong
)

private suspend fun ApplicationCall.getUserId(): UUID? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("sub")?.asString()?.let { UUID.fromString(it) }
}

fun Route.likeRoutes(collectionService: CollectionService) {
    route("/likes") {
        authenticate("auth-bearer") {

            // Get user's liked content
            get {
                val userId = call.getUserId()!!
                val contentType = call.request.queryParameters["contentType"]?.let {
                    try { ContentType.valueOf(it.uppercase()) } catch (_: Exception) { null }
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val offset = call.request.queryParameters["offset"]?.toIntOrNull()

                val likedItems = collectionService.getUserLikedContent(userId, contentType, limit, offset)
                call.respond(likedItems)
            }

            // Like content
            post {
                val userId = call.getUserId()!!
                val request = call.receive<LikeRequest>()

                val liked = collectionService.likeContent(userId, request.contentType, request.contentId)
                if (liked) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Content liked successfully"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Failed to like content (may already be liked)")
                }
            }

            // Unlike content
            delete {
                val userId = call.getUserId()!!

                val contentType = call.request.queryParameters["contentType"]?.let {
                    try { ContentType.valueOf(it.uppercase()) } catch (_: Exception) { null }
                } ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentType")

                val contentId = call.request.queryParameters["contentId"]?.toULongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentId")

                val unliked = collectionService.unlikeContent(userId, contentType, contentId)
                if (unliked) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Content unliked successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Content was not liked")
                }
            }

            // Check if content is liked
            get("/check") {
                val userId = call.getUserId()!!

                val contentType = call.request.queryParameters["contentType"]?.let {
                    try { ContentType.valueOf(it.uppercase()) } catch (_: Exception) { null }
                } ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentType")

                val contentId = call.request.queryParameters["contentId"]?.toULongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentId")

                val isLiked = collectionService.isContentLiked(userId, contentType, contentId)
                call.respond(mapOf("isLiked" to isLiked))
            }
        }

        // Get content statistics (public endpoint)
        get("/stats") {
            val contentType = call.request.queryParameters["contentType"]?.let {
                try { ContentType.valueOf(it.uppercase()) } catch (_: Exception) { null }
            } ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentType")

            val contentId = call.request.queryParameters["contentId"]?.toULongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentId")

            val stats = collectionService.getContentInteractionStats(contentType, contentId)
            call.respond(stats)
        }
    }
}
