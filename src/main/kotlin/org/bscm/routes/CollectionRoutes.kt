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
import org.bscm.models.dto.user.CatalogCounts
import org.bscm.models.dto.user.ItemsPage
import org.bscm.models.enums.CollectionKind
import org.bscm.services.CollectionService
import org.bscm.utils.*
import java.util.*

fun Route.collectionRoutes(collectionService: CollectionService) {
    route("/collections") {
        authenticate("auth-bearer") {
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
                 *   - 200 Updated collection slug (if name changed)
                 */
                put {
                    val userId = call.getUserId()
                    val collectionId = call.getId()
                    val request = call.receive<UpdateCollectionRequest>()
                    try {
                        val slug = collectionService.updateCollection(collectionId, userId, request.name, request.isPublic)
                        call.respond(HttpStatusCode.OK, mapOf("slug" to slug))
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
                     * Query: types [String] Optional list of content types to filter by (comma-separated, e.g. "chart,tourpass").
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
                        val categories = call.getContentTypeOrNull()
                        val (limit, offset) = call.getPagination()

                        val (items, counts) = collectionService.getCollectionItems(
                            userId = userId,
                            collectionId = collectionId,
                            categories = categories,
                            limit = limit,
                            offset = offset
                        )

                        call.respond(
                            ItemsPage(
                                items,
                                counts?.let { CatalogCounts(it.first, it.second, it.third) }
                            )
                        )
                    }

                    /**
                     * Add item to collection.
                     *
                     * Tag: Collections
                     *
                     * Path: id [UUID] Collection ID.
                     * Body: application/json Catalog ID to add [CreateCollectionItemRequest].
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
                        val added = collectionService.addItem(userId, request.catalogId, CollectionKind.USER, collectionId)
                        if (added) call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to collection"))
                        else call.respond(HttpStatusCode.BadRequest, "Failed to add item (may already exist or collection not found)")
                    }


                    /**
                     * Remove item from collection.
                     *
                     * Tag: Collections
                     *
                     * Path: id [UUID] Collection ID.
                     * Path: itemId [String] Catalog ID to remove.
                     *
                     * Responses:
                     *   - 401 User not authenticated.
                     *   - 404 Item not found in collection.
                     *   - 200 Success message.
                     */
                    delete("/{id}/items/{itemId}") {
                        val userId = call.getUserId()
                        val collectionId = call.getId()
                        val catalogId = call.getCatalogId("itemId")
                        val removed = collectionService.removeItem(userId, catalogId, CollectionKind.USER, collectionId)
                        if (removed) call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from collection"))
                        else call.respond(HttpStatusCode.NotFound, "Item not found in collection")
                    }
                }
            }
        }
    }
}
