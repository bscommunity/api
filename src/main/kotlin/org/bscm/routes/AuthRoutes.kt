package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.bscm.models.User
import org.bscm.models.dto.account.CreateAccountRequest
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.models.interfaces.IUserRepository
import org.bscm.services.auth.DiscordOAuthService
import org.bscm.services.auth.GoogleOAuthService
import org.bscm.services.auth.JWTService
import java.util.*
import kotlin.time.Duration.Companion.days

private val log = KtorSimpleLogger("AuthRoutes")

fun Route.authRoutes(
    userRepository: IUserRepository,
    discordOAuthService: DiscordOAuthService,
    googleOAuthService: GoogleOAuthService,
    jwtService: JWTService
) {
    route("/auth") {
        /**
         * Authenticate with Discord OAuth.
         *
         * Tag: Auth
         *
         * Body: application/json Discord OAuth authorization code and optional redirect URI [AuthRequest].
         *
         * Responses:
         *   - 400 Invalid request body or missing email in Discord user account.
         *   - 500 Internal server error during authentication.
         *   - 200 [AuthResponse] Successfully authenticated. Returns user info with access and refresh tokens.
         */
        post("/discord") {
            /*val authRequest = call.receiveOrNull<AuthRequest>()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "Invalid request body")*/

            val authRequest = try {
                call.receive<AuthRequest>()
            } catch (e: Exception) {
                return@post call.respondError(HttpStatusCode.BadRequest, "Invalid request body")
            }

            // log.info("authRequest: $authRequest")

            try {
                val accessToken = discordOAuthService.getAccessToken(authRequest)
                val discordUser = discordOAuthService.getUserInfo(accessToken)

                if (discordUser.email == null) {
                    call.respondError(HttpStatusCode.BadRequest, "Discord user must have an email")
                    return@post
                }

                val user = userRepository.getUserByDiscordId(discordUser.id)
                    ?.let { existingUser ->
                        userRepository.updateUser(
                            existingUser.id, UpdateUserRequest(
                                username = discordUser.username,
                                email = discordUser.email,
                                avatarUrl = discordUser.avatar?.let {
                                    discordOAuthService.getAvatarUrl(discordUser.id, it)
                                },
                                bannerUrl = discordUser.banner?.let {
                                    discordOAuthService.getBannerUrl(discordUser.id, it)
                                },
                                accentColor = discordUser.accentColor
                            ))
                    }
                    ?: userRepository.createUser(
                        CreateUserRequest(
                            username = discordUser.username,
                            email = discordUser.email,
                            discordId = discordUser.id,
                            avatarUrl = discordUser.avatar?.let {
                                discordOAuthService.getAvatarUrl(discordUser.id, it)
                            },
                            bannerUrl = discordUser.banner?.let {
                                discordOAuthService.getBannerUrl(discordUser.id, it)
                            },
                            accentColor = discordUser.accentColor
                        )
                    )

                log.info("User authenticated via Discord: $user")

                call.respond(user.toAuthResult(jwtService))
            } catch (e: Exception) {
                log.error("Error during Discord authentication: ${e.message}")
                call.respondError(HttpStatusCode.InternalServerError, "Authentication failed: ${e.message}")
            }
        }

        /**
         * Refresh access token using refresh token.
         *
         * Tag: Auth
         *
         * Body: application/json Refresh token obtained from previous authentication [RefreshTokenRequest].
         *
         * Responses:
         *   - 400 Invalid request body format.
         *   - 401 Invalid or expired refresh token.
         *   - 404 User associated with token not found.
         *   - 200 [AuthResponse] New access and refresh tokens with user info.
         */
        post("/refresh") {
            val refreshRequest = call.receive<RefreshTokenRequest>()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "Invalid request body")

            val userId = jwtService.verifyRefreshToken(refreshRequest.refreshToken)
                ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Invalid refresh token")

            val user = userRepository.getUserById(userId)
                ?: return@post call.respondError(HttpStatusCode.NotFound, "User not found")

            call.respond(user.toAuthResult(jwtService))
        }

        authenticate("auth-bearer") {
        /**
         * Get current authenticated user information.
         *
         * Tag: Auth
         *
         * Responses:
         *   - 401 Missing or invalid JWT token.
         *   - 404 User not found.
         *   - 200 [User] Current user details.
         */
            get("/me") {
                val userId = call.getUserIdFromJWT() ?: return@get
                val user = userRepository.getUserById(userId)
                    ?: return@get call.respondError(HttpStatusCode.NotFound, "User not found")

                call.respond(user)
            }
        }

        // Google OAuth linking and unlinking
        authenticate("auth-bearer", optional = true) {
            /**
             * Link Google account to current user.
             *
             * Tag: Auth
             *
             * Body: application/json Google OAuth authorization code [AuthRequest].
             *
             * Responses:
             *   - 400 Invalid request body or missing Google user scope.
             *   - 401 User not authenticated.
             *   - 500 Failed to authenticate with Google.
             *   - 200 [OAuthResult] Account linked successfully with OAuth scope.
             */
            post("/google/link") {
                val code = call.receive<AuthRequest>()?.code
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "Invalid request body")
                val userId = call.getUserIdFromJWT() ?: return@post

                try {
                    val googleTokenResponse = googleOAuthService.getAccessToken(code)

                    if (googleTokenResponse.scope.isNullOrBlank()) {
                        call.respondError(HttpStatusCode.BadRequest, "Google user must have a valid scope")
                        return@post
                    }

                    userRepository.upsertAccount(
                        userId,
                        CreateAccountRequest(
                            provider = "google",
                            refreshToken = googleTokenResponse.refreshToken,
                            accessToken = googleTokenResponse.accessToken,
                            expiresAt = (Clock.System.now() + googleTokenResponse.expiresIn.toLong().days).toLocalDateTime(TimeZone.currentSystemDefault()),
                            tokenType = googleTokenResponse.tokenType,
                            scope = googleTokenResponse.scope,
                        )
                    )

                    call.respond(OAuthResult(scope = googleTokenResponse.scope))
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to authenticate with Google: ${e.message}")
                }
            }

            /**
             * Unlink Google account from current user.
             *
             * Tag: Auth
             *
             * Responses:
             *   - 400 Google account not linked.
             *   - 401 User not authenticated.
             *   - 500 Failed to unlink account.
             *   - 200 Success message confirming account unlink.
             */
            post("/google/unlink") {
                val userId = call.getUserIdFromJWT() ?: return@post

                try {
                    userRepository.deleteAccount(userId)
                    call.respond(mapOf("message" to "Google account unlinked successfully"))
                } catch (e: NotFoundException) {
                    call.respond(mapOf("message" to "Google account not linked"))
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to unlink Google account")
                }
            }
        }
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? {
    return try {
        receive<T>()
    } catch (_: Exception) {
        null
    }
}

private suspend fun ApplicationCall.getUserIdFromJWT(): UUID? {
    val jwtPrincipal = principal<JWTPrincipal>()
        ?: return null.also { respondError(HttpStatusCode.Unauthorized, "Unauthorized access") }

    return jwtPrincipal.subject?.let { UUID.fromString(it) }
        ?: null.also { respondError(HttpStatusCode.BadRequest, "Invalid user ID") }
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) {
    respond(status, mapOf("error" to message))
}

// Extension functions for data transformation
private fun User.toAuthResult(jwtService: JWTService) = AuthResponse(
    accessToken = jwtService.generateAccessToken(id),
    refreshToken = jwtService.generateRefreshToken(id),
    expiresIn = jwtService.getAccessTokenExpiresIn(),
    user = this
)

// Simplified data classes
@Serializable
data class AuthRequest(
    val code: String,
    val redirectUri: String? = null,
    val codeVerifier: String? = null
)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
    val user: User
)

@Serializable
data class OAuthResult(val scope: String)