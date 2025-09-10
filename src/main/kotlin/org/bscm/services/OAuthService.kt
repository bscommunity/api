package org.bscm.services

// This class has been split into DiscordOAuthService and GoogleOAuthService.
// The original code has been migrated to the new files.

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bscm.plugins.applicationHttpClient

@Deprecated("Use DiscordOAuthService or GoogleOAuthService instead")
class OAuthService(
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String
) {
    private val discordApiEndpoint = "https://discord.com/api/v10";
    private val googleApiEndpoint = "https://www.googleapis.com";

    @Serializable
    data class DiscordUser(
        val id: String,
        val username: String,
        val email: String?,
        val avatar: String?
    )

    @Serializable
    data class GoogleUser(
        val id: String,
        val email: String,
        @SerialName("verified_email") val verifiedEmail: Boolean,
        val name: String?,
        @SerialName("picture") val imageUrl: String?
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String,
        @SerialName("expires_in") val expiresIn: Int
    )

    @Serializable
    data class GoogleTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("scope") val scope: String? = null,
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("id_token") val idToken: String? = null
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

        // Ensure the response status is successful
        if (response.status.isSuccess()) {
            // Parse the response as JSON
            val tokenResponse = response.body<TokenResponse>()
            return tokenResponse.accessToken
        } else {
            // Handle error response
            val errorResponse = response.body<TokenRequestError>()
            throw Exception("Failed to get access token: ${errorResponse.errorDescription}")
        }
    }

    suspend fun getUserInfo(
        accessToken: String
    ): DiscordUser = applicationHttpClient.get("$discordApiEndpoint/users/@me") {
        headers {
            append(HttpHeaders.Authorization, "Bearer $accessToken")
        }
    }.body()

    suspend fun getGoogleAccessToken(code: String): GoogleTokenResponse {
        val response: HttpResponse = applicationHttpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("client_secret", clientSecret)
            }
        )
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val errorBody = response.bodyAsText()
            throw Exception("Failed to get Google access token: $errorBody")
        }
    }

    suspend fun getGoogleUserInfo(accessToken: String): GoogleUser =
        applicationHttpClient.get("$googleApiEndpoint/oauth2/v2/userinfo") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.body()

    fun getAvatarUrl(userId: String, avatarHash: String): String {
        return "https://cdn.discordapp.com/avatars/$userId/$avatarHash.png"
    }
}
