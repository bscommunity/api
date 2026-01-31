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
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.services.ProfileService
import java.util.*

fun Route.userRoutes(
    userRepository: IUserRepository,
    profileService: ProfileService,
    activityRepository: IActivityRepository
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
             * Retrieves a user's public profile header.
             *
             * Path parameters:
             * - id: User UUID (required).
             *
             * Responses:
             * - 200 OK with the profile header when found.
             * - 404 Not Found if the user does not exist.
             * - 400 Bad Request (IllegalArgumentException) if the id parameter is missing/invalid.
             */
            get("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Invalid or missing ID")

                val requesterId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                val response = profileService.getProfileHeader(id, requesterId)
                call.respond(response)
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

                val requesterId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                val targetUser = userRepository.getUserByUsername(username)
                    ?: throw NotFoundException("User not found")

                val response = profileService.getProfileHeader(targetUser.id, requesterId)
                call.respond(response)
            }

            /**
             * GET /users/{id}/overview
             *
             * Retrieves a curated snapshot of recent content/activity for a user.
             */
            get("{id}/overview") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Invalid or missing ID")
                val requesterId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }

                val overview = profileService.getOverview(id, requesterId)
                call.respond(overview)
            }

            /**
             * GET /users/{id}/activity
             *
             * Retrieves a paginated chronological feed of user activity.
             */
            get("{id}/activity") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Invalid or missing ID")
                val requesterId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceAtMost(50) ?: 20
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val activity = profileService.getActivity(id, requesterId, limit, offset)
                call.respond(activity)
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
                    activityRepository.logActivity(
                        userId = followerId,
                        type = ActivityType.FOLLOWED_USER,
                        targetId = followedId.toString()
                    )
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