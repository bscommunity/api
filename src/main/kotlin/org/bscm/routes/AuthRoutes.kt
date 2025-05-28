package org.bscm.routes

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bscm.models.User
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.repository.UserRepository
import org.bscm.services.JWTService
import org.bscm.services.OAuthService

fun Route.authRoutes(
    userRepository: UserRepository,
    oAuthService: OAuthService,
    jwtService: JWTService
) {
    post("/login") {
        val code = call.receiveNullable<AuthRequest>()?.code ?: throw IllegalArgumentException("Code is required")

        try {
            val accessToken = oAuthService.getAccessToken(code)
            val discordUser = oAuthService.getUserInfo(accessToken)

            if (discordUser.email == null) {
                throw IllegalArgumentException("Discord user must have an email")
            }

            val user = userRepository.getUserByDiscordId(discordUser.id)
                ?.let { existingUser ->
                    userRepository.updateUser(existingUser.id, UpdateUserRequest(
                        username = discordUser.username,
                        email = discordUser.email,
                        imageUrl = discordUser.avatar?.let {
                            oAuthService.getAvatarUrl(discordUser.id, it)
                        }
                    ))
                }
                ?: throw NotImplementedError("User creation is not implemented yet")
                /*userRepository.createUser(
                    CreateUserRequest(
                        username = discordUser.username,
                        email = discordUser.email,
                        imageUrl = discordUser.avatar?.let {
                            oAuthService.getAvatarUrl(discordUser.id, it)
                        },
                        discordId = discordUser.id
                    )
                )*/

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