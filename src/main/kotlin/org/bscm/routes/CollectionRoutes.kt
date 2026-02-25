package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.collection.BatchCollectionItemRequest
import org.bscm.models.dto.collection.CreateCollectionItemRequest
import org.bscm.models.dto.collection.CreateCollectionRequest
import org.bscm.models.dto.collection.UpdateCollectionRequest
import org.bscm.services.CollectionService
import org.bscm.utils.*
import java.util.*

fun Route.collectionRoutes(collectionService: CollectionService) {
    route("/collections") {
        authenticate("auth-bearer") {
            install(org.bscm.plugins.UserContext)

            /**
             * Get user's collections.
             *
             * Tag: Collections
             *
             * Path: id [String] User ID (use "me" for current user).
             * Query: limit [Integer] Optional limit for results.
             * Query: offset [Integer] Optional pagination offset.
             *
             * Responses:
             *   - 401 User not authenticated.
             *   - 200 List of user's collections.
             */
            get("{userId}") {
                val requesterUserId = call.getUserId()
                val userId = call.pathParameters["userId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw IllegalArgumentException("Invalid user ID format")
                val isMe = userId == requesterUserId

                val (limit, offset) = call.getPagination()

                val response = collectionService.getUserCollections(userId, limit, offset, !isMe)
                call.respond(response)
            }

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
             */
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

            /**
             * Batch process multiple items (add or remove) in a single operation.
             *
             * Tag: Collections
             *
             * Body: application/json List of items with actions [BatchCollectionItemRequest].
             *
             * Responses:
             *   - 400 Invalid request parameters.
             *   - 401 User not authenticated.
             *   - 200 Batch operation summary.
             */
            post("batch") {
                val userId = call.getUserId()
                val request = call.receive<List<BatchCollectionItemRequest>>()

                if (request.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Request must contain at least one item")
                    )
                    return@post
                }

                val response = collectionService.processBatchCollectionItems(userId, request)
                call.respond(HttpStatusCode.OK, response)
            }

            get("/slug/{username}/{slug}") {
                val requesterUserId = call.getUserId()
                val username = call.pathParameters["username"] ?: throw IllegalArgumentException("Username is required")
                val slug = call.pathParameters["slug"] ?: throw IllegalArgumentException("Slug is required")

                val collection = collectionService.getCollectionBySlug(username, slug, requesterUserId)
                if (collection != null) {
                    call.respond(collection)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Collection not found or you don't have permission")
                }
            }

            // Generalized routes with id
            route("/{id}") {
                get {
                    val userId = call.getUserId()
                    val collectionId = call.getId()
                    val collection = collectionService.getCollection(collectionId, userId)
                    if (collection != null) {
                        call.respond(collection)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Collection not found or you don't have permission")
                    }
                }

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
                 */
                put {
                    val userId = call.getUserId()
                    val collectionId = call.getId()
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
                 */
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
                     */
                    get {
                        val userId = call.getUserId()
                        val collectionId = call.getId()
                        val category = call.getContentTypeOrNull()
                        val (limit, offset) = call.getPagination()
                        val items = collectionService.getCollectionItems(collectionId, userId, category, limit, offset)
                        println("Fetched ${items.size} items for collection $collectionId with category filter '$category'")
                        call.respond(items)
                    }


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
                     */
                    post {
                        val userId = call.getUserId()
                        val collectionId = call.getId()

                        val request = call.receive<CreateCollectionItemRequest>()

                        val added = collectionService.addItemToCollection(collectionId, userId, request.contentId)
                        if (added) {
                            call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to collection"))
                        } else {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                "Failed to add item (may already exist or collection not found)"
                            )
                        }
                    }


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
                     */
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
}
