package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.services.AuthService

fun Route.authRoutes(authService: AuthService) {
    authenticate("auth-oauth-discord") {
        get("/login") {
            // Redirects to "authorizeUrl" from Security.kt automatically
        }

        get("/callback") {
            val currentPrincipal: OAuthAccessTokenResponse.OAuth2? = call.principal()

            if (currentPrincipal?.accessToken == null) {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                return@get
            }

            try {
                val result = authService.handleDiscordCallback(currentPrincipal.accessToken)
                call.respond(result) // Return user and access token to client
            } catch (e: BadRequestException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Bad Request")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal Server Error")
            }
        }
    }
}