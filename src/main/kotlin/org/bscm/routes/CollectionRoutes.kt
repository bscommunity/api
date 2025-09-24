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
import org.bscm.models.enums.InteractionType
import org.bscm.plugins.UnauthorizedException
import org.bscm.services.CollectionService
import java.util.*

@Serializable
data class CreateCollectionRequest(
    val name: String,
    val isPublic: Boolean = false
)

@Serializable
data class UpdateCollectionRequest(
    val name: String? = null,
    val isPublic: Boolean? = null
)

@Serializable
data class CollectionItem(
    val contentType: ContentType,
    val contentId: ULong
)

// Batch processing data classes
@Serializable
data class BatchInteractionRequest(
    val interactions: List<InteractionRequest>
)

@Serializable
data class InteractionRequest(
    val contentType: ContentType,
    val contentId: String,
    val collectionId: String? = null,
    val interactionType: InteractionType,
)

private fun ApplicationCall.getUserId(): UUID {
    val principal = principal<JWTPrincipal>()
    return principal?.subject?.let { UUID.fromString(it) } ?: throw UnauthorizedException("User not authenticated")
}

fun Route.collectionRoutes(collectionService: CollectionService) {
    route("/collections") {
        authenticate("auth-bearer") {
            get("/{id}") {
                val collectionId = call.parameters["id"]?.toULongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid collection ID")

                val userId = call.getUserId()
                val collection = collectionService.getCollection(collectionId, userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Collection not found")

                call.respond(collection)
            }

            // Get collection items
            get("/{id}/items") {
                val collectionId = call.parameters["id"]?.toULongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid collection ID")

                val userId = call.getUserId()
                val items = collectionService.getCollectionItems(collectionId, userId)
                call.respond(items)
            }

            // Get user's collections
            get("/my") {
                val userId = call.getUserId()
                val collections = collectionService.getUserCollections(userId)
                call.respond(collections)
            }

            // Create new collection
            post {
                val userId = call.getUserId()
                val request = call.receive<CreateCollectionRequest>()

                try {
                    val collection = collectionService.createCollection(userId, request.name, request.isPublic)
                    call.respond(HttpStatusCode.Created, collection)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }

            // Update collection
            put("/{id}") {
                val userId = call.getUserId()
                val collectionId = call.parameters["id"]?.toULongOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid collection ID")

                val request = call.receive<UpdateCollectionRequest>()

                try {
                    val updated =
                        collectionService.updateCollection(collectionId, userId, request.name, request.isPublic)
                    if (updated) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Collection updated successfully"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Collection not found or you don't have permission")
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }

            // Delete collection
            delete("/{id}") {
                val userId = call.getUserId()
                val collectionId = call.parameters["id"]?.toULongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid collection ID")

                val deleted = collectionService.deleteCollection(collectionId, userId)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Collection deleted successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Collection not found or you don't have permission")
                }
            }

            // Add item to collection
            post("/{id}/items") {
                val userId = call.getUserId()
                val collectionId = call.parameters["id"]?.toULongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid collection ID")

                val request = call.receive<CollectionItem>()

                val added =
                    collectionService.addItemToCollection(collectionId, userId, request.contentType, request.contentId)
                if (added) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to collection"))
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Failed to add item (may already exist or collection not found)"
                    )
                }
            }

            // Remove item from collection
            delete("/{id}/items") {
                val userId = call.getUserId()
                val collectionId = call.parameters["id"]?.toULongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid collection ID")

                val contentType = call.request.queryParameters["contentType"]?.let {
                    try {
                        ContentType.valueOf(it.uppercase())
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentType")

                val contentId = call.request.queryParameters["contentId"]?.toULongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentId")

                val removed = collectionService.removeItemFromCollection(collectionId, userId, contentType, contentId)
                if (removed) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from collection"))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Item not found in collection")
                }
            }

            // Get user's collections containing specific item
            get("/containing") {
                val userId = call.getUserId()

                val contentType = call.request.queryParameters["contentType"]?.let {
                    try {
                        ContentType.valueOf(it.uppercase())
                    } catch (e: Exception) {
                        null
                    }
                } ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentType")

                val contentId = call.request.queryParameters["contentId"]?.toULongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentId")

                val collections = collectionService.getUserCollectionsContaining(userId, contentType, contentId)
                call.respond(collections)
            }

            // Batch process interactions
            post("/batch") {
                val userId = call.getUserId()
                val request = call.receive<BatchInteractionRequest>()

                try {
                    val response = collectionService.batchProcessInteractions(userId, request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
                }
            }
        }
    }

    // Favorites convenience routes
    route("/favorites") {
        authenticate("auth-bearer") {

            // Get user's favorites
            get {
                val userId = call.getUserId()
                val favorites = collectionService.getUserFavorites(userId)
                call.respond(favorites)
            }

            // Add item to favorites
            post {
                val userId = call.getUserId()
                val request = call.receive<CollectionItem>()

                val added = collectionService.addToFavorites(userId, request.contentType, request.contentId)
                if (added) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to favorites"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Failed to add item to favorites (may already exist)")
                }
            }

            // Remove item from favorites
            delete {
                val userId = call.getUserId()

                val contentType = call.request.queryParameters["contentType"]?.let {
                    try {
                        ContentType.valueOf(it.uppercase())
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentType")

                val contentId = call.request.queryParameters["contentId"]?.toULongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentId")

                val removed = collectionService.removeFromFavorites(userId, contentType, contentId)
                if (removed) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from favorites"))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Item not found in favorites")
                }
            }

            // Check if item is in favorites
            get("/check") {
                val userId = call.getUserId()

                val contentType = call.request.queryParameters["contentType"]?.let {
                    try {
                        ContentType.valueOf(it.uppercase())
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentType")

                val contentId = call.request.queryParameters["contentId"]?.toULongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentId")

                val isFavorited = collectionService.isInFavorites(userId, contentType, contentId)
                call.respond(mapOf("isFavorited" to isFavorited))
            }
        }
    }

    // Like routes (merged from LikeRoutes.kt)
    route("/likes") {
        authenticate("auth-bearer") {

            // Get user's liked content
            get {
                val userId = call.getUserId()
                val contentType = call.request.queryParameters["contentType"]?.let {
                    try {
                        ContentType.valueOf(it.uppercase())
                    } catch (_: Exception) {
                        null
                    }
                }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val offset = call.request.queryParameters["offset"]?.toIntOrNull()

                val likedItems = collectionService.getUserLikedContent(userId, contentType, limit, offset)
                call.respond(likedItems)
            }

            // Like content
            post {
                val userId = call.getUserId()
                val request = call.receive<CollectionItem>()

                val liked = collectionService.likeContent(userId, request.contentType, request.contentId)
                if (liked) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Content liked successfully"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Failed to like content (may already be liked)")
                }
            }

            // Unlike content
            delete {
                val userId = call.getUserId()

                val contentType = call.request.queryParameters["contentType"]?.let {
                    try {
                        ContentType.valueOf(it.uppercase())
                    } catch (_: Exception) {
                        null
                    }
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
                val userId = call.getUserId()

                val contentType = call.request.queryParameters["contentType"]?.let {
                    try {
                        ContentType.valueOf(it.uppercase())
                    } catch (_: Exception) {
                        null
                    }
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
                try {
                    ContentType.valueOf(it.uppercase())
                } catch (_: Exception) {
                    null
                }
            } ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentType")

            val contentId = call.request.queryParameters["contentId"]?.toULongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing contentId")

            val stats = collectionService.getContentInteractionStats(contentType, contentId)
            call.respond(stats)
        }
    }
}
