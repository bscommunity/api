package org.bscm.plugins

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.response.*

import org.bscm.auth.DISCORD_API_ENDPOINT
import org.bscm.services.JWTService
import org.koin.ktor.ext.inject

val redirects = mutableMapOf<String, String>()

fun Application.configureSecurity(
    config: ApplicationConfig
) {
    val jwtService by inject<JWTService>()

    val secret = config.property("jwt.secret").getString()
    val jwtRealm = config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .build()
            )
            validate { credential ->
                val userId = credential.subject?.let { jwtService.verifyToken(it) }
                if (userId != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
            realm = jwtRealm
        }

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