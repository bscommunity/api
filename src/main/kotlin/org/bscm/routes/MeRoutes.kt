package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.user.ContentCounts
import org.bscm.models.dto.user.ItemsPage
import org.bscm.models.enums.CollectionKind
import org.bscm.models.interfaces.IChartRepository
import org.bscm.repository.ChartRepository
import org.bscm.services.CollectionService
import org.bscm.services.ProfileService
import org.bscm.utils.getPagination
import org.bscm.utils.getUserId


fun Route.meRoutes(
    collectionService: CollectionService,
    profileService: ProfileService,
    chartRepository: IChartRepository
) {
    route("/me") {
        install(org.bscm.plugins.UserContext)

        authenticate("auth-bearer") {
            // ===================== CHARTS ======================

            /**
             * Get authenticated user's charts.
             *
             * Tag: Me
             *
             * Query: limit [Integer] Optional limit for results (max 50).
             * Query: offset [Integer] Optional pagination offset (default 0).
             *
             * Responses:
             *   - 200 application/json [Array] List of user's charts.
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            get("/charts") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination(coerceLimit = 50, defaultOffset = 0)

                val (charts, _) = chartRepository.getCharts(
                    filters = ChartRepository.ChartFilters(userId = userId),
                    addons = ChartRepository.ChartAddons(versions = true),
                    limit = limit,
                    offset = offset,
                )

                call.respond(charts)
            }

            // ==================== PROFILE ====================

            /**
             * Get authenticated user's profile header.
             *
             * Tag: Me
             *
             * Responses:
             *   - 200 application/json [Object] User's profile header.
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            get("/profile") {
                val userId = call.getUserId()
                val response = profileService.getProfileHeader(userId, userId)
                call.respond(response)
            }

            /**
             * Get authenticated user's activity feed.
             *
             * Tag: Me
             *
             * Query: limit [Integer] Optional limit for results (max 50).
             * Query: offset [Integer] Optional pagination offset (default 0).
             *
             * Responses:
             *   - 200 application/json [Array] Paginated list of activities.
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            get("/activity") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination(coerceLimit = 50, defaultOffset = 0)

                val activity = profileService.getActivity(userId, userId, limit ?: 20, offset ?: 0)
                call.respond(activity)
            }

            // ==================== LIKES ====================

            /**
             * Get authenticated user's liked items.
             * Returns [ItemsPage] with the paginated items and total content counts (charts / tourPasses / themes).
             * Counts are always for the full collection, regardless of the current page.
             */
            get("/likes") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()
                val (items, counts) = collectionService.getCollectionItemsWithCounts(
                    userId, CollectionKind.LIKES, limit = limit, offset = offset
                )
                call.respond(ItemsPage(items, ContentCounts(counts.first, counts.second, counts.third)))
            }

            /**
             * Add item to user's likes.
             *
             * Tag: Me
             *
             * Path: contentId [String] ID of the content to like.
             *
             * Responses:
             *   - 200 application/json [Object] Success message.
             *   - 400 application/json [Error] Failed to add item (may already exist).
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            post("/likes/{contentId}") {
                val userId = call.getUserId()
                val contentId = call.parameters["contentId"] ?: throw IllegalArgumentException("Missing contentId")
                val added = collectionService.addItem(userId, contentId, CollectionKind.LIKES)
                if (added) call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to likes"))
                else call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to add item (may already exist)"))
            }

            /**
             * Remove item from user's likes.
             *
             * Tag: Me
             *
             * Path: contentId [String] ID of the content to unlike.
             *
             * Responses:
             *   - 200 application/json [Object] Success message.
             *   - 401 application/json [Error] User not authenticated.
             *   - 404 application/json [Error] Item not found in likes.
             *
             * Security: auth-bearer
             */
            delete("/likes/{contentId}") {
                val userId = call.getUserId()
                val contentId = call.parameters["contentId"] ?: throw IllegalArgumentException("Missing contentId")
                val removed = collectionService.removeItem(userId, contentId, CollectionKind.LIKES)
                if (removed) call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from likes"))
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Item not found in likes"))
            }

            // ==================== BOOKMARKS ====================

            /**
             * Get authenticated user's bookmarked items.
             * Returns [ItemsPage] with the paginated items and total content counts (charts / tourPasses / themes).
             */
            get("/bookmarks") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()
                val (items, counts) = collectionService.getCollectionItemsWithCounts(
                    userId, CollectionKind.BOOKMARKS, limit = limit, offset = offset
                )
                call.respond(ItemsPage(items, ContentCounts(counts.first, counts.second, counts.third)))
            }

            /**
             * Add item to user's bookmarks.
             *
             * Tag: Me
             *
             * Path: contentId [String] ID of the content to bookmark.
             *
             * Responses:
             *   - 200 application/json [Object] Success message.
             *   - 400 application/json [Error] Failed to add item (may already exist).
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            post("/bookmarks/{contentId}") {
                val userId = call.getUserId()
                val contentId = call.parameters["contentId"] ?: throw IllegalArgumentException("Missing contentId")
                val added = collectionService.addItem(userId, contentId, CollectionKind.BOOKMARKS)
                if (added) call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to bookmarks"))
                else call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to add item (may already exist)"))
            }

            /**
             * Remove item from user's bookmarks.
             *
             * Tag: Me
             *
             * Path: contentId [String] ID of the content to remove from bookmarks.
             *
             * Responses:
             *   - 200 application/json [Object] Success message.
             *   - 401 application/json [Error] User not authenticated.
             *   - 404 application/json [Error] Item not found in bookmarks.
             *
             * Security: auth-bearer
             */
            delete("/bookmarks/{contentId}") {
                val userId = call.getUserId()
                val contentId = call.parameters["contentId"] ?: throw IllegalArgumentException("Missing contentId")
                val removed = collectionService.removeItem(userId, contentId, CollectionKind.BOOKMARKS)
                if (removed) call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from bookmarks"))
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Item not found in bookmarks"))
            }

            // ==================== COLLECTIONS ====================

            /**
             * Get collections created by the authenticated user.
             * Returns [CollectionsPage] with the current page of collections and the total count.
             */
            get("/collections") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()
                val (collections, total) = collectionService.getUserCollections(userId, limit, offset, false)
                call.respond(ItemsPage(items = collections, counts = ContentCounts(collections = total)))
            }
        }
    }
}
