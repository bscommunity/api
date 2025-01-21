package org.bscm.auth

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.bscm.plugins.applicationHttpClient

const val DISCORD_API_ENDPOINT = "https://discord.com/api/v10";

object DiscordOAuth {
    @Serializable
    data class DiscordUser(
        val id: String,
        val username: String,
        val email: String?,
        val avatar: String?
    )

    suspend fun getUserInfo(
        accessToken: String
    ): DiscordUser = applicationHttpClient.get("$DISCORD_API_ENDPOINT/users/@me") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $accessToken")
        }
    }.body()

    fun getAvatarUrl(userId: String, avatarHash: String): String {
        return "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png"
    }
}
