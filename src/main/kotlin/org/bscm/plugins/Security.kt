package org.bscm.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateSerializer
import java.time.LocalDate

const val DISCORD_API_ENDPOINT = "https://discord.com/api/v10";
val redirects = mutableMapOf<String, String>()

fun Application.configureSecurity() {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }

    install(Authentication) {
        oauth("auth-oauth-discord") {
            urlProvider = { "http://localhost:8080/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "discord",
                    authorizeUrl = "https://discord.com/oauth2/authorize",
                    accessTokenUrl = "${DISCORD_API_ENDPOINT}/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("DISCORD_CLIENT_ID"),
                    clientSecret = System.getenv("DISCORD_CLIENT_SECRET"),
                    defaultScopes = listOf("identify", "email"),
                    onStateCreated = { call, state ->
                        // Saves new state with redirect url value, so, if the user had
                        // initially attempted to access a protected resource before authentication,
                        // the application can redirect the user back to that resource
                        call.request.queryParameters["redirectUrl"]?.let {
                            redirects[state] = it
                        }
                    }
                )
            }
            client = applicationHttpClient
        }
    }
}

suspend fun getDiscordData(
    httpClient: HttpClient,
    userSession: UserSession
): DiscordUser = httpClient.get("${DISCORD_API_ENDPOINT}/users/@me") {
    headers {
        append(HttpHeaders.Authorization, "Bearer ${userSession.token}")
    }
}.body()

suspend fun getSession(
    call: ApplicationCall
): UserSession? {
    val userSession: UserSession? = call.sessions.get()
    //if there is no session, redirect to log in
    if (userSession == null) {
        val redirectUrl = URLBuilder("http://0.0.0.0:8080/login").run {
            parameters.append("redirectUrl", call.request.uri)
            build()
        }
        call.respondRedirect(redirectUrl)
        return null
    }
    return userSession
}

@Serializable
data class UserSession(val state: String, val token: String)

@Serializable()
data class DiscordUser(
    val id: String,
    val username: String,
    @SerialName("global_name") val globalName: String,
    val discriminator: String?,
    val avatar: String?,
    val email: String? = null,
    val verified: Boolean
)