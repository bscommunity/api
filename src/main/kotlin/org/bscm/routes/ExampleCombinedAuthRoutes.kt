package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.plugins.CombinedPrincipal
import java.util.*

/**
 * Example routes demonstrating how to use combined JWT + HMAC authentication
 *
 * This ensures:
 * 1. The request comes from the mobile app (HMAC verification)
 * 2. The user is authenticated (JWT verification)
 */
fun Route.exampleCombinedAuthRoutes() {

    // Apply combined authentication to these routes
    authenticate("auth-combined") {

        get("/mobile/profile") {
            // Retrieve the combined principal
            val combined = call.principal<CombinedPrincipal>()

            if (combined != null) {
                // Access JWT information
                val userId = combined.jwtPrincipal.subject?.let { UUID.fromString(it) }
                val username = combined.jwtPrincipal.payload.getClaim("username").asString()
                val expiresAt = combined.jwtPrincipal.expiresAt?.time?.minus(System.currentTimeMillis())

                // Access HMAC information
                val appId = combined.hmacPrincipal.appId
                val timestamp = combined.hmacPrincipal.timestamp

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "message" to "Successfully authenticated with both JWT and HMAC",
                        "userId" to userId,
                        "username" to username,
                        "tokenExpiresIn" to expiresAt,
                        "appId" to appId,
                        "requestTimestamp" to timestamp
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Authentication failed")
                )
            }
        }

        get("/mobile/secure-data") {
            val combined = call.principal<CombinedPrincipal>()!!

            // Extract user ID from JWT
            val userId = UUID.fromString(combined.jwtPrincipal.subject)

            // Your business logic here
            // You can be confident that:
            // 1. The request is from the mobile app (HMAC verified)
            // 2. The user is authenticated (JWT verified)
            // 3. The request is recent (timestamp verified)

            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "This endpoint is only accessible from the mobile app with valid JWT",
                    "userId" to userId
                )
            )
        }
    }
}

