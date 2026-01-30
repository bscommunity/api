package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.models.dto.user.UserProfileResponse
import org.bscm.models.interfaces.IUserRepository
import org.bscm.services.CollectionService
import java.util.*

fun Route.userRoutes(
    userRepository: IUserRepository,
    collectionService: CollectionService
) {
    route("/users") {
        // Create a new user
        post {
            val createRequest = call.receive<CreateUserRequest>()
            val createdUser = userRepository.createUser(createRequest)
            call.respond(HttpStatusCode.Created, createdUser)
        }

        authenticate("auth-bearer", optional = true) {
            /**
             * GET /users
             *
             * Retrieves a list of users. Supports an optional `search` query parameter
             * to filter users by name/username.
             *
             * Query parameters:
             * - search: Optional search string to filter users.
             *
             * Response:
             * - 200 OK with the list of users.
             */
            get {
                // Get query parameters (search)
                val search = call.request.queryParameters["search"]

                val users = userRepository.getUsers(search)

                call.respond(users)
            }

            /**
             * GET /users/hello
             *
             * Test endpoint that reads JWT principal information and returns a simple
             * greeting including the username and token expiry time (in milliseconds).
             *
             * Security:
             * - Requires authentication (JWT principal).
             *
             * Response:
             * - 200 OK with a plain text greeting.
             */
            get("/hello") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
                call.respondText("Hello, $username! Token is expired at $expiresAt ms.")
            }

            /**
             * GET /users/{id}
             *
             * Retrieves a single user by their Discord ID.
             *
             * Path parameters:
             * - id: Discord ID (required).
             *
             * Responses:
             * - 200 OK with the user when found.
             * - 404 Not Found if the user does not exist.
             * - 400 Bad Request (IllegalArgumentException) if the id parameter is missing/invalid.
             */
            get("{id}") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Invalid or missing Discord ID")

                val user = userRepository.getUserByDiscordId(id)
                if (user != null) {
                    call.respond(user)
                } else {
                    throw NotFoundException("User not found")
                }
            }

            /**
             * GET /users/by-username/{username}
             *
             * Retrieves a user's profile by username, including related data such as
             * charts, collections (only if the requester is the owner), likes, bookmarks,
             * and aggregated statistics.
             *
             * Path parameters:
             * - username: Target user's username (required).
             *
             * Authentication:
             * - Reads JWT principal when present to identify the requesting user and
             *   determine ownership (affects limits and visible data).
             *
             * Query parameters:
             * - contentType: Optional filter for chart content type (case-insensitive).
             * - query: Optional search/query string for charts.
             * - limit: Optional limit for charts result set (owner and non-owner caps apply).
             * - offset: Optional pagination offset for charts (defaults to 0).
             *
             * Limits:
             * - charts limit: owner -> max 50 (default 50); non-owner -> max 20 (default 20).
             * - collections: only returned for owner (limit 10).
             * - likes/bookmarks: owner -> 50, non-owner -> 20.
             *
             * Responses:
             * - 200 OK with UserProfileResponse containing user, charts, collections,
             *   likes, bookmarks and stats.
             * - 404 Not Found if the user does not exist.
             * - 400 Bad Request (IllegalArgumentException) if required parameters are missing/invalid.
             */
            get("username/{username}") {
                val username = call.parameters["username"]
                    ?: throw IllegalArgumentException("Invalid or missing username")

                // Get the requesting user ID from JWT (if authenticated)
                val principal = call.principal<JWTPrincipal>()
                val requestingUserId = principal?.subject?.let { UUID.fromString(it) }

                // Get the target user
                val targetUser = userRepository.getUserByUsername(username)
                    ?: throw NotFoundException("User not found")

                // Determine if the requesting user is viewing their own profile
                val isOwner = requestingUserId == targetUser.id

                // Check privacy settings (non-owners cannot view private profiles)
                if (!isOwner && !targetUser.isPublic) {
                    throw NotFoundException("User profile is private")
                }

                // Parse query parameters
                val query = call.request.queryParameters["query"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                // Set limits based on ownership
                val chartsLimit = if (isOwner) {
                    limit?.coerceAtMost(50) ?: 50
                } else {
                    limit?.coerceAtMost(20) ?: 20
                }
                val collectionsLimit = if (isOwner) 10 else 0
                val likesBookmarksLimit = if (isOwner) 50 else 20
                // val followersFollowingLimit = if (isOwner) 20 else 10

                // Fetch user's charts
                val charts = userRepository.getUserCharts(
                    userId = targetUser.id,
                    requestingUserId = requestingUserId,
                    query = query,
                    limit = chartsLimit,
                    offset = offset
                )

                // Fetch collections (only for owner)
                val collections = if (isOwner) {
                    collectionService.getUserCollections(
                        userId = targetUser.id,
                        limit = collectionsLimit,
                        offset = 0
                    )
                } else {
                    null
                }

                // Fetch badges
                val badges = userRepository.getUserBadges(targetUser.id)

                // Fetch followers (only for owner, or if user's profile is public)
                /*val followers = if (isOwner || targetUser.isPublic) {
                    userRepository.getFollowers(
                        userId = targetUser.id,
                        limit = followersFollowingLimit,
                        offset = 0
                    )
                } else {
                    null
                }

                // Fetch following list
                val following = userRepository.getFollowing(
                    userId = targetUser.id,
                    limit = followersFollowingLimit,
                    offset = 0
                )*/

                // Fetch likes from system collection
                val likes = userRepository.getSystemCollectionItems(
                    userId = targetUser.id,
                    collectionName = "likes",
                    requestingUserId = requestingUserId,
                    limit = likesBookmarksLimit
                )

                // Fetch bookmarks from system collection (favorites)
                val bookmarks = userRepository.getSystemCollectionItems(
                    userId = targetUser.id,
                    collectionName = "favorites",
                    requestingUserId = requestingUserId,
                    limit = likesBookmarksLimit
                )

                // Get user stats
                val counts = userRepository.getProfileCounts(targetUser.id)

                // Build response
                val response = UserProfileResponse(
                    user = targetUser,
                    badges = badges,
                    followerCount = targetUser.followerCount,
                    followingCount = targetUser.followingCount,
                    isPublic = targetUser.isPublic,
                    isVerified = targetUser.isVerified,
                    counts = counts
                )

                call.respond(response)
            }

            /**
             * PUT /users/{id}
             *
             * Updates an existing user's mutable fields.
             *
             * Path parameters:
             * - id: UUID of the user to update (required).
             *
             * Request body:
             * - UpdateUserRequest DTO with fields to update.
             *
             * Responses:
             * - 200 OK (implicitly) if update succeeds.
             * - 404 Not Found if the user does not exist.
             * - 400 Bad Request (IllegalArgumentException) if the id parameter is missing/invalid.
             */
            put("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }
                val user = call.receive<UpdateUserRequest>()

                try {
                    userRepository.updateUser(id, user)
                } catch (e: NotFoundException) {
                    throw NotFoundException(e.message ?: "Not Found")
                }
            }

            /**
             * DELETE /users/{id}
             *
             * Deletes a user by UUID.
             *
             * Path parameters:
             * - id: UUID of the user to delete (required).
             *
             * Responses:
             * - 200 OK with a success message if deletion succeeded.
             * - 404 Not Found if the user does not exist.
             * - 400 Bad Request (IllegalArgumentException) if the id parameter is missing/invalid.
             */
            delete("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }
                val deleted = userRepository.deleteUser(id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, "User deleted successfully")
                } else {
                    throw NotFoundException("User not found")
                }
            }

            /**
             * POST /users/{id}/follow
             *
             * Follows a user (adds them to your following list).
             *
             * Path parameters:
             * - id: UUID of the user to follow (required).
             *
             * Security:
             * - Requires authentication (JWT principal).
             *
             * Responses:
             * - 200 OK with success message if follow succeeds.
             * - 400 Bad Request if already following or cannot follow self.
             * - 404 Not Found if the target user does not exist.
             */
            post("{id}/follow") {
                val principal = call.principal<JWTPrincipal>()
                val followerId = principal?.subject?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Authentication required")

                val followedId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Invalid or missing ID")

                val success = userRepository.followUser(followerId, followedId)
                if (success) {
                    call.respond(HttpStatusCode.OK, "User followed successfully")
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Cannot follow this user (already following or invalid)")
                }
            }

            /**
             * DELETE /users/{id}/follow
             *
             * Unfollows a user (removes them from your following list).
             *
             * Path parameters:
             * - id: UUID of the user to unfollow (required).
             *
             * Security:
             * - Requires authentication (JWT principal).
             *
             * Responses:
             * - 200 OK with success message if unfollow succeeds.
             * - 404 Not Found if the follow relationship does not exist.
             */
            delete("{id}/follow") {
                val principal = call.principal<JWTPrincipal>()
                val followerId = principal?.subject?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Authentication required")

                val followedId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Invalid or missing ID")

                val success = userRepository.unfollowUser(followerId, followedId)
                if (success) {
                    call.respond(HttpStatusCode.OK, "User unfollowed successfully")
                } else {
                    throw NotFoundException("Follow relationship not found")
                }
            }

            /**
             * GET /users/{id}/followers
             *
             * Retrieves a list of users who follow the specified user.
             *
             * Path parameters:
             * - id: UUID of the user (required).
             *
             * Query parameters:
             * - limit: Optional limit for results (default 10, max 20).
             * - offset: Optional pagination offset (defaults to 0).
             *
             * Responses:
             * - 200 OK with list of SimplifiedUser objects.
             * - 404 Not Found if the user does not exist.
             */
            get("{id}/followers") {
                val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Invalid or missing ID")

                // Verify user exists
                userRepository.getUserById(userId) ?: throw NotFoundException("User not found")

                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceAtMost(20) ?: 10
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val followers = userRepository.getFollowers(userId, limit, offset)
                call.respond(followers)
            }

            /**
             * GET /users/{id}/following
             *
             * Retrieves a list of users that the specified user follows.
             *
             * Path parameters:
             * - id: UUID of the user (required).
             *
             * Query parameters:
             * - limit: Optional limit for results (default 10, max 20).
             * - offset: Optional pagination offset (defaults to 0).
             *
             * Responses:
             * - 200 OK with list of SimplifiedUser objects.
             * - 404 Not Found if the user does not exist.
             */
            get("{id}/following") {
                val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Invalid or missing ID")

                // Verify user exists
                userRepository.getUserById(userId) ?: throw NotFoundException("User not found")

                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceAtMost(20) ?: 10
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val following = userRepository.getFollowing(userId, limit, offset)
                call.respond(following)
            }

            get("{id}/charts") {
                val userId = UUID.fromString(call.parameters["id"]!!)
                val requester = call.principal<JWTPrincipal>()?.subject?.let(UUID::fromString)

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val charts = userRepository.getUserCharts(
                    userId = userId,
                    requestingUserId = requester,
                    query = null,
                    limit = limit,
                    offset = offset
                )

                call.respond(charts)
            }
        }
    }
}