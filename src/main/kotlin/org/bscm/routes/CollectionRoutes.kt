package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.collection.CreateCollectionItemRequest
import org.bscm.models.dto.collection.CreateCollectionRequest
import org.bscm.models.dto.collection.UpdateCollectionRequest
import org.bscm.models.enums.ContentType
import org.bscm.plugins.UnauthorizedException
import org.bscm.services.CollectionService
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

fun Route.collectionRoutes(collectionService: CollectionService) {
    route("/collections") {
        authenticate("auth-bearer") {
            // Get user's custom collections
            get {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()
                val response = collectionService.getUserCollections(userId, limit, offset)
                call.respond(response)
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

            // Generalized routes with id
            route("/{id}") {
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
                    get {
                        val userId = call.getUserId()
                        val collectionId = call.getId()
                        val category = call.getContentTypeOrNull()
                        val (limit, offset) = call.getPagination()
                        val items = collectionService.getCollectionItems(collectionId, userId, category, limit, offset)
                        call.respond(items)
                    }

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
}

