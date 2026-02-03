package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.enums.CollectionKind
import org.bscm.plugins.UnauthorizedException
import org.bscm.repository.ChartRepository
import org.bscm.services.CollectionService
import org.bscm.services.ProfileService
import java.util.*

private fun ApplicationCall.getUserId(): UUID {
    val principal = principal<JWTPrincipal>()
    return principal?.subject?.let { UUID.fromString(it) } ?: throw UnauthorizedException("User not authenticated")
}

private fun ApplicationCall.getPagination(coerceLimit: Int? = null): Pair<Int?, Int> {
    val limit = request.queryParameters["limit"]?.toIntOrNull().let {
        if (coerceLimit != null) {
            it?.coerceAtMost(coerceLimit)
        } else {
            it
        }
    }
    val offset = request.queryParameters["offset"]?.toIntOrNull() ?: 0
    return limit to offset
}

fun Route.meRoutes(
    collectionService: CollectionService,
    profileService: ProfileService,
    chartRepository: ChartRepository
) {
    route("/me") {
        authenticate("auth-bearer") {
            // ===================== CHARTS ======================

            get("/charts") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination(50)

                val (charts, _) = chartRepository.getCharts(
                    filters = ChartRepository.ChartFilters(userId = userId),
                    addons = ChartRepository.ChartAddons(allVersions = true),
                    limit = limit,
                    offset = offset,
                )

                call.respond(charts)
            }

            // ==================== PROFILE ====================

            get("/profile") {
                val userId = call.getUserId()
                val response = profileService.getProfileHeader(userId, userId)
                call.respond(response)
            }

            get("/activity") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination(50)

                val activity = profileService.getActivity(userId, userId, limit ?: 20, offset)
                call.respond(activity)
            }

            // ==================== LIKES ====================

            // Get authenticated user's likes
            get("/likes") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()

                val items = collectionService.getSystemCollectionItems(
                    userId = userId,
                    kind = CollectionKind.LIKES,
                    limit = limit,
                    offset = offset
                )

                call.respond(items)
            }

            // Add item to likes
            post("/likes/{contentId}") {
                val userId = call.getUserId()
                val contentId = call.parameters["contentId"]
                    ?: throw IllegalArgumentException("Invalid or missing contentId")

                val added = collectionService.addToSystemCollection(userId, CollectionKind.LIKES, contentId)
                if (added) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to likes"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to add item (may already exist)"))
                }
            }

            // Remove item from likes
            delete("/likes/{contentId}") {
                val userId = call.getUserId()
                val contentId = call.parameters["contentId"]
                    ?: throw IllegalArgumentException("Invalid or missing contentId")

                val removed = collectionService.removeFromSystemCollection(userId, CollectionKind.LIKES, contentId)
                if (removed) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from likes"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Item not found in likes"))
                }
            }

            // ==================== BOOKMARKS ====================

            // Get authenticated user's bookmarks
            get("/bookmarks") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()

                val items = collectionService.getSystemCollectionItems(
                    userId = userId,
                    kind = CollectionKind.BOOKMARKS,
                    limit = limit,
                    offset = offset
                )

                call.respond(items)
            }

            // Add item to bookmarks
            post("/bookmarks/{contentId}") {
                val userId = call.getUserId()
                val contentId = call.parameters["contentId"]
                    ?: throw IllegalArgumentException("Invalid or missing contentId")

                val added = collectionService.addToSystemCollection(userId, CollectionKind.BOOKMARKS, contentId)
                if (added) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to bookmarks"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to add item (may already exist)"))
                }
            }

            // Remove item from bookmarks
            delete("/bookmarks/{contentId}") {
                val userId = call.getUserId()
                val contentId = call.parameters["contentId"]
                    ?: throw IllegalArgumentException("Invalid or missing contentId")

                val removed = collectionService.removeFromSystemCollection(userId, CollectionKind.BOOKMARKS, contentId)
                if (removed) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from bookmarks"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Item not found in bookmarks"))
                }
            }
        }
    }
}
