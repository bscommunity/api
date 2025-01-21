package org.bscm.services

import io.ktor.server.plugins.*
import kotlinx.serialization.Serializable
import org.bscm.auth.DiscordOAuth
import org.bscm.models.User
import org.bscm.models.dto.CreateUserRequest
import org.bscm.models.dto.UpdateUserRequest
import org.bscm.repository.UserRepository

class AuthService (
    private val userRepository: UserRepository,
    private val jwtService: JWTService
) {
    suspend fun handleDiscordCallback(
        accessToken: String,
    ): AuthResult {
        val discordUser = DiscordOAuth.getUserInfo(accessToken)

        println(discordUser.toString())

        if (discordUser.email == null) {
            throw BadRequestException("Discord user must have an email")
        }

        val user = userRepository.getUserByDiscordId(discordUser.id)
            ?.let { existingUser ->
                userRepository.updateUser(existingUser.id, UpdateUserRequest(
                    username = discordUser.username,
                    email = discordUser.email,
                    imageUrl = discordUser.avatar?.let {
                        DiscordOAuth.getAvatarUrl(discordUser.id, it)
                    }
                ))
            }
            ?: userRepository.createUser(CreateUserRequest(
                username = discordUser.username,
                email = discordUser.email,
                imageUrl = discordUser.avatar?.let {
                    DiscordOAuth.getAvatarUrl(discordUser.id, it)
                },
                discordId = discordUser.id
            ))

        return AuthResult(
            token = jwtService.generateToken(user.id),
            user = user
        )
    }
}

@Serializable
data class AuthResult(
    val token: String,
    val user: User
)