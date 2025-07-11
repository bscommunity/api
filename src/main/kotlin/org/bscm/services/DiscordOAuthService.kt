package org.bscm.services

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bscm.plugins.applicationHttpClient

class DiscordOAuthService(
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String
) {
    private val discordApiEndpoint = "https://discord.com/api/v10"

    @Serializable
    data class DiscordUser(
        val id: String,
        val username: String,
        val email: String?,
        val avatar: String?
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String,
        @SerialName("expires_in") val expiresIn: Int
    )

    @Serializable
    private data class TokenRequestError(
        @SerialName("error") val error: String,
        @SerialName("error_description") val errorDescription: String
    )

    suspend fun getAccessToken(code: String): String {
        val response: HttpResponse = applicationHttpClient.submitForm(
            url = "${discordApiEndpoint}/oauth2/token",
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("client_secret", clientSecret)
            }
        )
        if (response.status.isSuccess()) {
            val tokenResponse = response.body<TokenResponse>()
            return tokenResponse.accessToken
        } else {
            val errorResponse = response.body<TokenRequestError>()
            throw Exception("Failed to get access token: ${errorResponse.errorDescription}")
        }
    }

    suspend fun getUserInfo(accessToken: String): DiscordUser =
        applicationHttpClient.get("$discordApiEndpoint/users/@me") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.body()

    fun getAvatarUrl(userId: String, avatarHash: String): String {
        return "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png"
    }
}

