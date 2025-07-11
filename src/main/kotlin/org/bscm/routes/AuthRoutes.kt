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
import java.time.LocalDate
import java.util.*

fun Route.authRoutes(
    userRepository: UserRepository,
    discordOAuthService: DiscordOAuthService,
    googleOAuthService: GoogleOAuthService,
    jwtService: JWTService
) {
    route("/auth") {
        post("/login") {
            val code = call.receiveAndValidateAuthCode() ?: return@post

            try {
                val accessToken = discordOAuthService.getAccessToken(code)
                val discordUser = discordOAuthService.getUserInfo(accessToken)

                if (discordUser.email == null) {
                    throw IllegalArgumentException("Discord user must have an email")
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
                // ?: throw NotImplementedError("User creation is not implemented yet")
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

                call.respond(
                    AuthResult(
                        token = jwtService.generateToken(user.id),
                        user = user
                    )
                )
            } catch (e: Exception) {
                println(e.message)
                throw IllegalArgumentException(e.message ?: "Failed to authenticate")
            }
        }

        authenticate("auth-jwt", optional = true) {
            post("/google/link") {
                val code = call.receiveAndValidateAuthCode() ?: return@post

                val jwtPrincipal = call.principal<JWTPrincipal>()

                if (jwtPrincipal == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized access")
                    return@post
                }

                val userId = jwtPrincipal.subject?.let { UUID.fromString(it) }

                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
                    return@post
                }

                try {
                    val googleTokenResponse = googleOAuthService.getAccessToken(code)
                    println("Google token response: $googleTokenResponse")
                    // val googleUser = googleOAuthService.getUserInfo(googleTokenResponse.accessToken)

                    /*if (!googleUser.verifiedEmail) {
                        call.respond(HttpStatusCode.BadRequest, "Google user must have a verified email")
                        return@post
                    }*/

                    if (googleTokenResponse.scope.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "Google user must have a valid scope")
                        return@post
                    }

                    userRepository.upsertAccount(
                        userId,
                        CreateAccountRequest(
                            provider = "google",
                            // providerAccountId = googleUser.id,
                            refreshToken = googleTokenResponse.refreshToken,
                            accessToken = googleTokenResponse.accessToken,
                            expiresAt = LocalDate.now().plusDays(googleTokenResponse.expiresIn.toLong()),
                            tokenType = googleTokenResponse.tokenType,
                            scope = googleTokenResponse.scope,
                        )
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        OAuthResult(
                            scope = googleTokenResponse.scope
                        )
                    )
                } catch (e: Exception) {
                    println(e.message)
                    throw IllegalArgumentException(e.message ?: "Failed to authenticate with Google")
                }
            }

            post("google/unlink") {
                val jwtPrincipal = call.principal<JWTPrincipal>()

                if (jwtPrincipal == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized access")
                    return@post
                }

                val userId = jwtPrincipal.subject?.let { UUID.fromString(it) }

                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
                    return@post
                }

                try {
                    userRepository.deleteAccount(userId)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Google account unlinked successfully"))
                } catch (e: NotFoundException) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Google account not linked"))
                } catch (e: Exception) {
                    println(e.message)
                    throw IllegalArgumentException(e.message ?: "Failed to unlink Google account")
                }
            }
        }
    }
}

private suspend fun ApplicationCall.receiveAndValidateAuthCode(): String? {
    val authRequest = try {
        receive<AuthRequest>()
    } catch (e: Exception) {
        respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Invalid request body. Expected JSON with 'code' field.")
        )
        return null
    }
    val code = authRequest.code
    if (code.isBlank()) {
        respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Code cannot be empty")
        )
        return null
    }
    return code
}

@Serializable
data class AuthRequest(
    val code: String
)

@Serializable
data class AuthResult(
    val token: String,
    val user: User
)

@Serializable
data class OAuthResult(
    val scope: String,
)