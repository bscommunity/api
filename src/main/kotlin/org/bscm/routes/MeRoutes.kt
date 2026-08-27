package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.user.CatalogCounts
import org.bscm.models.dto.user.ItemsPage
import org.bscm.models.enums.*
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.repository.ChartRepository
import org.bscm.services.CollectionService
import org.bscm.services.ProfileService
import org.bscm.utils.getContentTypeOrNull
import org.bscm.utils.getPagination
import org.bscm.utils.getUserId


fun Route.meRoutes(
    collectionService: CollectionService,
    profileService: ProfileService,
    chartRepository: IChartRepository,
    userRepository: IUserRepository,
) {
    route("/me") {
        authenticate("auth-bearer") {
            // ===================== UPLOADS ======================

            /**
             * Get authenticated user's uploads (all content types, interleaved).
             *
             * Tag: Me
             *
             * Query: types [String] Optional comma-separated content types to filter (CHART, TOUR_PASS, THEME).
             * Query: query [String] Optional search query across all types.
             * Query: sortBy [String] Sort option (LAST_UPDATED, MOST_DOWNLOADED, ALPHA_ASC, ALPHA_DESC).
             * Query: limit [Integer] Optional limit for results (max 50).
             * Query: offset [Integer] Optional pagination offset (default 0).
             *
             * Responses:
             *   - 200 application/json [Object] Paginated items with content counts.
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            get("/uploads") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination(coerceLimit = 50, defaultOffset = 0)

                val requestedTypes = call.getContentTypeOrNull()
                val query = call.request.queryParameters["query"]?.takeIf { it.isNotBlank() }
                val sortBy = call.request.queryParameters["sortBy"]
                    ?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }

                val genres = call.request.queryParameters["genres"]
                    ?.split(",")
                    ?.mapNotNull { runCatching { Genre.valueOf(it.trim()) }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }

                val difficulties = call.request.queryParameters["difficulties"]
                    ?.split(",")
                    ?.mapNotNull { runCatching { Difficulty.valueOf(it.trim()) }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }

                val isDeluxe = call.request.queryParameters["versions"]
                    ?.split(",")
                    ?.any { it.trim().equals("DELUXE", ignoreCase = true) }

                val includeVersions = call.request.queryParameters["includeVersions"]?.toBoolean() == true

                val (items, counts) = userRepository.getUserUploads(
                    userId = userId,
                    types = requestedTypes,
                    query = query,
                    sortBy = sortBy,
                    genres = genres,
                    difficulties = difficulties,
                    isDeluxe = isDeluxe,
                    limit = limit ?: 20,
                    offset = offset ?: 0,
                    includeVersions = includeVersions,
                )

                call.respond(
                    ItemsPage(
                        items,
                        CatalogCounts(counts.first, counts.second, counts.third)
                    )
                )
            }
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
                    requestingUserId = userId,
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
             * Counts are only returned for the first page (offset=0) to optimize performance
             */
            get("/likes") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()

                // Parse ?types=charts,themes,tourPasses — null means "all"
                val requestedTypes = call.request.queryParameters["types"]
                    ?.split(",")
                    ?.mapNotNull { runCatching { CatalogItemType.valueOf(it.trim()) }.getOrNull() }

                val (items, counts) = collectionService.getSystemCollectionItems(
                    userId = userId,
                    kind = CollectionKind.LIKES,
                    limit = limit,
                    offset = offset,
                    categories = requestedTypes  // pass null = all types
                )

                println("User $userId requested likes with limit=$limit, offset=$offset, types=$requestedTypes. Returning ${items.size} items and counts=$counts")

                call.respond(
                    ItemsPage(
                        items,
                        counts?.let { CatalogCounts(it.first, it.second, it.third) }
                    )
                )
            }

            /**
             * Add item to user's likes.
             *
             * Tag: Me
             *
             * Path: catalogId [String] ID of the content to like.
             *
             * Responses:
             *   - 200 application/json [Object] Success message.
             *   - 400 application/json [Error] Failed to add item (may already exist).
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            post("/likes/{catalogId}") {
                val userId = call.getUserId()
                val catalogId = call.parameters["catalogId"] ?: throw IllegalArgumentException("Missing catalogId")
                val added = collectionService.addItem(userId, catalogId, CollectionKind.LIKES)
                if (added) call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to likes"))
                else call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to add item (may already exist)"))
            }

            /**
             * Remove item from user's likes.
             *
             * Tag: Me
             *
             * Path: catalogId [String] ID of the content to unlike.
             *
             * Responses:
             *   - 200 application/json [Object] Success message.
             *   - 401 application/json [Error] User not authenticated.
             *   - 404 application/json [Error] Item not found in likes.
             *
             * Security: auth-bearer
             */
            delete("/likes/{catalogId}") {
                val userId = call.getUserId()
                val catalogId = call.parameters["catalogId"] ?: throw IllegalArgumentException("Missing catalogId")
                val removed = collectionService.removeItem(userId, catalogId, CollectionKind.LIKES)
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

                // Parse ?types=charts,themes,tourPasses — null means "all"
                val requestedTypes = call.request.queryParameters["types"]
                    ?.split(",")
                    ?.mapNotNull { runCatching { CatalogItemType.valueOf(it.trim()) }.getOrNull() }

                val (items, counts) = collectionService.getSystemCollectionItems(
                    userId = userId,
                    kind = CollectionKind.BOOKMARKS,
                    limit = limit,
                    offset = offset,
                    categories = requestedTypes  // pass null = all types
                )

                println("User $userId requested bookmarks with limit=$limit, offset=$offset, types=$requestedTypes. Returning ${items.size} items and counts=$counts")

                call.respond(
                    ItemsPage(
                        items,
                        counts?.let { CatalogCounts(it.first, it.second, it.third) }
                    )
                )
            }

            /**
             * Add item to user's bookmarks.
             *
             * Tag: Me
             *
             * Path: catalogId [String] ID of the content to bookmark.
             *
             * Responses:
             *   - 200 application/json [Object] Success message.
             *   - 400 application/json [Error] Failed to add item (may already exist).
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            post("/bookmarks/{catalogId}") {
                val userId = call.getUserId()
                val catalogId = call.parameters["catalogId"] ?: throw IllegalArgumentException("Missing catalogId")
                val added = collectionService.addItem(userId, catalogId, CollectionKind.BOOKMARKS)
                if (added) call.respond(HttpStatusCode.OK, mapOf("message" to "Item added to bookmarks"))
                else call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to add item (may already exist)"))
            }

            /**
             * Remove item from user's bookmarks.
             *
             * Tag: Me
             *
             * Path: catalogId [String] ID of the content to remove from bookmarks.
             *
             * Responses:
             *   - 200 application/json [Object] Success message.
             *   - 401 application/json [Error] User not authenticated.
             *   - 404 application/json [Error] Item not found in bookmarks.
             *
             * Security: auth-bearer
             */
            delete("/bookmarks/{catalogId}") {
                val userId = call.getUserId()
                val catalogId = call.parameters["catalogId"] ?: throw IllegalArgumentException("Missing catalogId")
                val removed = collectionService.removeItem(userId, catalogId, CollectionKind.BOOKMARKS)
                if (removed) call.respond(HttpStatusCode.OK, mapOf("message" to "Item removed from bookmarks"))
                else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Item not found in bookmarks"))
            }

            // ==================== COLLECTIONS ====================

            /**
             * Get collections created by the authenticated user.
             * Returns [ItemsPage] with the paginated collections and total collection count.
             */
            get("/collections") {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination()
                val (collections, total) = collectionService.getUserCollections(userId, limit, offset, false)
                call.respond(ItemsPage(items = collections, counts = CatalogCounts(collections = total)))
            }
        }
    }
}
