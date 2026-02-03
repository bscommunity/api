package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.bscm.models.UserContext
import org.bscm.repository.BaseRepository
import java.util.*

/**
 * Plugin that captures authenticated user context from JWT tokens for use in repositories.
 * This must be installed AFTER authentication to properly extract the principal.
 */
val UserContextPlugin = createRouteScopedPlugin(name = "UserContextPlugin") {
    onCall { call ->
        try {
            // Try to extract userId from JWT (runs after authentication)
            val jwtPrincipal = call.principal<JWTPrincipal>()
            val combinedPrincipal = call.principal<CombinedPrincipal>()

            val userId = when {
                combinedPrincipal != null -> {
                    val id = UUID.fromString(combinedPrincipal.jwtPrincipal.subject)
                    println("[UserContextPlugin] Extracted userId from CombinedPrincipal: $id from ${call.request.local.uri}")
                    id
                }
                jwtPrincipal != null -> {
                    val id = jwtPrincipal.subject?.let { UUID.fromString(it) }
                    println("[UserContextPlugin] Extracted userId from JWTPrincipal: $id from ${call.request.local.uri}")
                    id
                }
                else -> {
                    println("[UserContextPlugin] No principal found, userId is null from ${call.request.local.uri}")
                    null
                }
            }

            BaseRepository.setUserContext(UserContext(userId))
        } catch (e: Exception) {
            // If parsing fails, set anonymous context
            println("[UserContextPlugin] Exception parsing userId: ${e.message}, setting anonymous context")
            BaseRepository.setUserContext(UserContext(null))
        }
    }

    onCallRespond { _, _ ->
        println("[UserContextPlugin] Clearing UserContext")
        BaseRepository.clearUserContext()
    }
}