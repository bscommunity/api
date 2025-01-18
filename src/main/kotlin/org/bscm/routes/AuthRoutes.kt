package org.bscm.routes

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.headers
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.bscm.plugins.*
import java.net.http.HttpResponse

fun Route.authRoutes() {
    /*route("/auth") {*/
        authenticate("auth-oauth-discord") {
            get("/login") {
                // Redirects to "authorizeUrl" from Security.kt automatically
            }

            get("/callback") {
                val currentPrincipal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                println("Current principal: $currentPrincipal")

                // Redirects to "/" if the url is not found before authorization
                currentPrincipal?.let { principal ->
                    principal.state?.let { state ->
                        call.sessions.set(UserSession(state, principal.accessToken))
                        redirects[state]?.let { redirect ->
                            call.respondRedirect(redirect)
                            return@get
                        }
                    }
                }

                println("Redirecting to /, since the url is not found before authorization")
                call.respondRedirect("/")
            }
        }

        get("/{path}") {
            val userSession: UserSession? = getSession(call)
            if (userSession != null) {
                val userInfo: DiscordUser = getDiscordData(httpClient = applicationHttpClient, userSession)
                call.respondText("Hello, ${userInfo.globalName}! + $userInfo")
            }
        }

        post("/register") {
            // Register route
        }
    /*}*/
}