package org.bscm.routes

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.bscm.models.dto.collection.CreateCollectionItemRequest
import org.bscm.models.dto.collection.CreateCollectionRequest
import org.bscm.models.dto.collection.UpdateCollectionRequest
import org.bscm.models.enums.ContentType
import org.bscm.plugins.UnauthorizedException
import java.util.*

private fun ApplicationCall.getUserId(): UUID {
    val principal = principal<JWTPrincipal>()
    return principal?.subject?.let { UUID.fromString(it) } ?: throw UnauthorizedException("User not authenticated")
}

private fun ApplicationCall.getContentTypeOrNull(): ContentType? {
    val typeParam = request.queryParameters["contentType"]
    return typeParam?.let {
        try {
            ContentType.valueOf(it.uppercase())
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

private fun ApplicationCall.getId(paramName: String = "id"): UUID {
    val idString = parameters[paramName] ?: throw IllegalArgumentException("Invalid or missing $paramName")
    return try {
        UUID.fromString(idString)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("$paramName must be a valid UUID")
    }
}

private fun ApplicationCall.getContentId(paramName: String = "itemId"): String {
    return parameters[paramName] ?: throw IllegalArgumentException("Invalid or missing $paramName")
}

private fun ApplicationCall.getPagination(): Pair<Int?, Int?> {
    val limit = request.queryParameters["limit"]?.toIntOrNull()
    val offset = request.queryParameters["offset"]?.toIntOrNull()
    return limit to offset
}

/*
fun Route.collectionRoutes(collectionService: CollectionService) {
    route("/collections") {
        authenticate("auth-bearer") {
            install(org.bscm.plugins.UserContext)
            */
/**
             * Get authenticated user's custom collections.
             *
             * Tag: Collections
             *
             * Query: limit [Integer] Optional limit for results.
             * Query: offset [Integer] Optional pagination offset.
             *
             * Responses:
             *   - 401 User not authenticated.
             *   - 200 List of user's collections.
             *//*

            // Get user's custom collections
            get {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()
                val response = collectionService.getUserCollections(userId, limit, offset)
                call.respond(response)
            }

            */
/**
             * Create a new collection.
             *
             * Tag: Collections
             *
             * Body: application/json Collection name and visibility settings [CreateCollectionRequest].
             *
             * Responses:
             *   - 400 Invalid request parameters.
             *   - 401 User not authenticated.
             *   - 201 Created collection.
             *//*

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

            // Generalized routes with id
            route("/{id}") {
                */
/**
                 * Update collection metadata.
                 *
                 * Tag: Collections
                 *
                 * Path: id [UUID] Collection ID.
                 * Body: application/json Updated collection information [UpdateCollectionRequest].
                 *
                 * Responses:
                 *   - 400 Invalid request parameters.
                 *   - 401 User not authenticated.
                 *   - 404 Collection not found or no permission.
                 *   - 200 Success message.
                 *//*

                // Update collection
                put {
                    val userId = call.getUserId()
                    val collectionId = call.getId()
                    val request = call.receive<UpdateCollectionRequest>()
                    try {
                        val updated = collectionService.updateCollection(collectionId, userId, request.name, request.isPublic)
                        if (updated) {
                            call.respond(HttpStatusCode.OK, mapOf("message" to "Collection updated successfully"))
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Collection not found or you don't have permission")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    }
                }

                */
/**
                 * Delete a collection.
                 *
                 * Tag: Collections
                 *
                 * Path: id [UUID] Collection ID.
                 *
                 * Responses:
                 *   - 401 User not authenticated.
                 *   - 404 Collection not found or no permission.
                 *   - 200 Success message.
                 *//*

                // Delete collection
                delete {
                    val userId = call.getUserId()
                    val collectionId = call.getId()
                    val deleted = collectionService.deleteCollection(collectionId, userId)
                    if (deleted) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Collection deleted successfully"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Collection not found or you don't have permission")
                    }
                }

                route("/items") {
                    */
/**
                     * Get collection items.
                     *
                     * Tag: Collections
                     *
                     * Path: id [UUID] Collection ID.
                     * Query: contentType [String] Optional content type filter.
                     * Query: limit [Integer] Optional limit for results.
                     * Query: offset [Integer] Optional pagination offset.
                     *
                     * Responses:
                     *   - 401 User not authenticated.
                     *   - 404 Collection not found.
                     *   - 200 List of collection items.
                     *//*

                    get {
                        val userId = call.getUserId()
                        val collectionId = call.getId()
                        val category = call.getContentTypeOrNull()
                        val (limit, offset) = call.getPagination()
                        val items = collectionService.getCollectionItems(collectionId, userId, category, limit, offset)
                        call.respond(items)
                    }

                    */
/**
                     * Add item to collection.
                     *
                     * Tag: Collections
                     *
                     * Path: id [UUID] Collection ID.
                     * Body: application/json Content ID to add [CreateCollectionItemRequest].
                     *
                     * Responses:
                     *   - 400 Failed to add item (may already exist).
                     *   - 401 User not authenticated.
                     *   - 404 Collection not found.
                     *   - 200 Success message.
                     *//*

                    // Add item to collection
                    post {
                        val userId = call.getUserId()
                        val collectionId = call.getId()

                        val request = call.receive<CreateCollectionItemRequest>()

                        val added = collectionService.addItemToCollection(collectionId, userId, request.contentId)
                        if (added) {
                            call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to collection"))
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Failed to add item (may already exist or collection not found)")
                        }
                    }

                    */
/**
                     * Remove item from collection.
                     *
                     * Tag: Collections
                     *
                     * Path: id [UUID] Collection ID.
                     * Path: itemId [String] Content ID to remove.
                     *
                     * Responses:
                     *   - 401 User not authenticated.
                     *   - 404 Item not found in collection.
                     *   - 200 Success message.
                     *//*

                    // Remove item from collection
                    delete("{itemId}") {
                        val userId = call.getUserId()
                        val collectionId = call.getId()
                        val contentId = call.getContentId("itemId")
                        val removed = collectionService.removeItemFromCollection(collectionId, userId, contentId)
                        if (removed) {
                            call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from collection"))
                        } else {
                            call.respond(HttpStatusCode.NotFound, "Item not found in collection")
                        }
                    }
                }
            }
        }
    }
}*/
