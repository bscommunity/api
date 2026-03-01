package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.user.*
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.services.CollectionService
import org.bscm.services.ProfileService
import org.bscm.utils.getPagination
import org.bscm.utils.getUserIdOrNull
import java.util.*

fun Route.userRoutes(
    userRepository: IUserRepository,
    profileService: ProfileService,
    collectionService: CollectionService,
    activityRepository: IActivityRepository
) {
    route("/users") {
        /**
         * Create a new user.
         *
         * Tag: Users
         *
         * Body: application/json User information to create [CreateUserRequest].
         *
         * Response: 201 application/json Newly created user [User].
         * Response: 400 application/json Invalid request body.
         */
        post {
            val createRequest = call.receive<CreateUserRequest>()
            val createdUser = userRepository.createUser(createRequest)
            call.respond(HttpStatusCode.Created, createdUser)
        }

        authenticate("auth-bearer", optional = true) {
            install(org.bscm.plugins.UserContext)

            /**
             * Get user profile header by username.
             *
             * Tag: Users
             *
             * Path: username [String] User's username.
             * Query: counts [String] Optional comma-separated list of counts to include (e.g. "charts,likes").
             *
             * Responses:
             *   - 400 Username parameter is malformatted or missing.
             *   - 404 User not found.
             *   - 200 User profile header.
             */
            get("username/{username}") {
                val username = call.parameters["username"]
                    ?: throw IllegalArgumentException("Invalid or missing username")
                val requesterId = call.getUserIdOrNull()
                val response = profileService.getProfileHeaderByUsername(username, requesterId)
                call.respond(response)
            }

            /**
             * List users with optional search filter.
             *
             * Tag: Users
             *
             * Query: search [String] Optional search string to filter users by name/username.
             *
             * Response: 200 application/json List of users matching search criteria.
             * Response: 400 application/json Invalid query parameters.
             */
            get {
                // Get query parameters (search)
                val search = call.request.queryParameters["search"]

                val users = userRepository.getUsers(search)

                call.respond(users)
            }

            /**
             * Get user profile header by ID.
             *
             * Tag: Users
             *
             * Path: id [UUID] User UUID.
             *
             * Responses:
             *   - 400 ID parameter is malformatted or missing.
             *   - 404 User not found.
             *   - 200 User profile header.
             */
            get("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: throw IllegalArgumentException("Invalid or missing ID")
                val requesterId = call.principal<JWTPrincipal>()?.subject?.let { UUID.fromString(it) }
                val response = profileService.getProfileHeader(id, requesterId)
                call.respond(response)
            }

            /**
             * Get user activity feed.
             *
             * Tag: Users
             *
             * Path: id [UUID] User UUID.
             * Query: limit [Integer] Optional limit for results (default 20, max 50).
             * Query: offset [Integer] Optional pagination offset (default 0).
             *
             * Responses:
             *   - 400 ID parameter is malformatted or missing.
             *   - 404 User not found.
             *   - 200 Paginated list of user activities.
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
             * Update existing user.
             *
             * Tag: Users
             *
             * Path: id [UUID] UUID of the user to update.
             * Body: application/json Fields to update [UpdateUserRequest].
             *
             * Responses:
             *   - 400 ID parameter is malformatted or missing.
             *   - 404 User not found.
             *   - 200 [User] Updated user.
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
             * Delete user by ID.
             *
             * Tag: Users
             *
             * Path: id [UUID] UUID of the user to delete.
             *
             * Responses:
             *   - 400 ID parameter is malformatted or missing.
             *   - 404 User not found.
             *   - 200 Success message.
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
             * Follow a user.
             *
             * Tag: Users
             *
             * Path: id [UUID] UUID of the user to follow.
             *
             * Responses:
             *   - 400 Already following or cannot follow self.
             *   - 401 Authentication required.
             *   - 404 Target user not found.
             *   - 200 Success message.
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
             * Unfollow a user.
             *
             * Tag: Users
             *
             * Path: id [UUID] UUID of the user to unfollow.
             *
             * Responses:
             *   - 401 Authentication required.
             *   - 404 Follow relationship not found.
             *   - 200 Success message.
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
             * Get followers of a user.
             *
             * Tag: Users
             *
             * Path: id [UUID] UUID of the user.
             * Query: limit [Integer] Optional limit for results (default 10, max 20).
             * Query: offset [Integer] Optional pagination offset (default 0).
             *
             * Responses:
             *   - 400 ID parameter is malformatted or missing.
             *   - 404 User not found.
             *   - 200 List of users who follow the specified user.
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
             * Get users followed by a user.
             *
             * Tag: Users
             *
             * Path: id [UUID] UUID of the user.
             * Query: limit [Integer] Optional limit for results (default 10, max 20).
             * Query: offset [Integer] Optional pagination offset (default 0).
             *
             * Responses:
             *   - 400 ID parameter is malformatted or missing.
             *   - 404 User not found.
             *   - 200 List of users followed by the specified user.
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

            /**
             * Get charts by user.
             *
             * Tag: Users
             *
             * Path: id [UUID] UUID of the user.
             * Query: limit [Integer] Optional limit for results (default 20).
             * Query: offset [Integer] Optional pagination offset (default 0).
             *
             * Responses:
             *   - 400 Invalid or missing ID parameter.
             *   - 404 User not found.
             *   - 200 List of user's charts with library counts.
             */
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

                // Get library counts (charts, tour passes, themes) for this user
                val libraryCounts = userRepository.getLibraryCounts(userId)

                println("Fetched ${charts.size} charts for user $userId (requester: $requester, limit: $limit, offset: $offset)")

                call.respond(ItemsPage(charts, ContentCounts(libraryCounts.first, libraryCounts.second, libraryCounts.third)))
            }

            /**
             * Get user's collections.
             *
             * Tag: Users
             *
             * Path: id [String] User ID (use "me" for current user).
             * Query: limit [Integer] Optional limit for results.
             * Query: offset [Integer] Optional pagination offset.
             *
             * Responses:
             *   - 401 User not authenticated.
             *   - 200 List of user's collections with total count.
             */
            get("{id}/collections") {
                val requesterUserId = call.getUserIdOrNull()
                val userId = call.pathParameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw IllegalArgumentException("Invalid user ID format")
                val isMe = userId == requesterUserId

                val (limit, offset) = call.getPagination()

                val (collections, total) = collectionService.getUserCollections(userId, limit, offset, !isMe)
                call.respond(CollectionsPage(items = collections, total = total))
            }
        }
    }
}