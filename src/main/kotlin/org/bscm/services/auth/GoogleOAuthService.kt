package org.bscm.services.auth

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bscm.services.track.clients.applicationHttpClient

class GoogleOAuthService(
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String
) {
    private val googleApiEndpoint = "https://www.googleapis.com"

    @Serializable
    data class GoogleUser(
        val id: String,
        val email: String,
        @SerialName("verified_email") val verifiedEmail: Boolean,
        val name: String?,
        @SerialName("picture") val imageUrl: String?
    )

    @Serializable
    data class GoogleTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("refresh_token") val refreshToken: String? = null,
        // @SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Int,
        @SerialName("scope") val scope: String? = null,
        @SerialName("token_type") val tokenType: String? = null,
    )

    suspend fun getAccessToken(code: String): GoogleTokenResponse {
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

    suspend fun getUserInfo(accessToken: String): GoogleUser =
        applicationHttpClient.get("$googleApiEndpoint/oauth2/v2/userinfo") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.body()
}

