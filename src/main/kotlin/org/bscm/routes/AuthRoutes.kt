package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bscm.models.User
import org.bscm.models.dto.account.CreateAccountRequest
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.repository.UserRepository
import org.bscm.services.DiscordOAuthService
import org.bscm.services.GoogleOAuthService
import org.bscm.services.JWTService
import java.time.LocalDateTime
import java.util.*

fun Route.authRoutes(
    userRepository: UserRepository,
    discordOAuthService: DiscordOAuthService,
    googleOAuthService: GoogleOAuthService,
    jwtService: JWTService
) {
    route("/auth") {
        post("/discord") {
            val authRequest = call.receiveOrNull<AuthRequest>()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "Invalid request body")

            println("authRequest: $authRequest")

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
                                imageUrl = discordUser.avatar?.let {
                                    discordOAuthService.getAvatarUrl(discordUser.id, it)
                                }
                            ))
                    }
                    ?: userRepository.createUser(
                        CreateUserRequest(
                            username = discordUser.username,
                            email = discordUser.email,
                            imageUrl = discordUser.avatar?.let {
                                discordOAuthService.getAvatarUrl(discordUser.id, it)
                            },
                            discordId = discordUser.id
                        )
                    )

                println("User authenticated via Discord: ${user.id}")

                call.respond(user.toAuthResult(jwtService))
            } catch (e: Exception) {
                println("Error during Discord authentication: ${e.message}")
                call.respondError(HttpStatusCode.InternalServerError, "Authentication failed: ${e.message}")
            }
        }

        post("/refresh") {
            val refreshRequest = call.receiveOrNull<RefreshTokenRequest>()
                ?: return@post call.respondError(HttpStatusCode.BadRequest, "Invalid request body")

            val userId = jwtService.verifyRefreshToken(refreshRequest.refreshToken)
                ?: return@post call.respondError(HttpStatusCode.Unauthorized, "Invalid refresh token")

            val user = userRepository.getUserById(userId)
                ?: return@post call.respondError(HttpStatusCode.NotFound, "User not found")

            call.respond(user.toAuthResult(jwtService))
        }

        authenticate("auth-bearer") {
            get("/me") {
                val userId = call.getUserIdFromJWT() ?: return@get
                val user = userRepository.getUserById(userId)
                    ?: return@get call.respondError(HttpStatusCode.NotFound, "User not found")

                call.respond(user)
            }
        }

        // Google OAuth linking and unlinking
        authenticate("auth-bearer", optional = true) {
            post("/google/link") {
                val code = call.receiveAndValidateAuthCode() ?: return@post
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
                            expiresAt = LocalDateTime.now().plusDays(googleTokenResponse.expiresIn.toLong()),
                            tokenType = googleTokenResponse.tokenType,
                            scope = googleTokenResponse.scope,
                        )
                    )

                    call.respond(OAuthResult(scope = googleTokenResponse.scope))
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to authenticate with Google: ${e.message}")
                }
            }

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

// Extension functions for cleaner code
private suspend fun ApplicationCall.receiveAndValidateAuthCode(): String? {
    val authRequest = receiveOrNull<AuthRequest>()
        ?: return null.also { respondError(HttpStatusCode.BadRequest, "Invalid request body") }

    return authRequest.code.takeIf { it.isNotBlank() }
        ?: null.also { respondError(HttpStatusCode.BadRequest, "Code cannot be empty") }
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
    val redirectUri: String?,
    val codeVerifier: String?
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