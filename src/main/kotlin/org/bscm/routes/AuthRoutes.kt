package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bscm.models.User
import org.bscm.models.dto.CreateUserRequest
import org.bscm.models.dto.UpdateUserRequest
import org.bscm.repository.UserRepository
import org.bscm.services.JWTService
import org.bscm.services.OAuthService

fun Route.authRoutes(
    userRepository: UserRepository,
    oAuthService: OAuthService,
    jwtService: JWTService
) {
    post("/login") {
        val code = call.receiveNullable<AuthRequest>()?.code

        if (code == null) {
            call.respond(HttpStatusCode.BadRequest, "Code is required")
            return@post
        }

        val accessToken = oAuthService.getAccessToken(code)
        val discordUser = oAuthService.getUserInfo(accessToken)

        if (discordUser.email == null) {
            call.respond(HttpStatusCode.BadRequest, "Discord user must have an email")
            return@post
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
            ?: userRepository.createUser(
                CreateUserRequest(
                    username = discordUser.username,
                    email = discordUser.email,
                    imageUrl = discordUser.avatar?.let {
                        oAuthService.getAvatarUrl(discordUser.id, it)
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