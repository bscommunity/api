package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.util.logging.*
import org.bscm.models.UserContext
import org.bscm.repository.BaseRepository
import java.util.*

private val log = KtorSimpleLogger("UserContextPlugin")

/**
 * Plugin that captures authenticated user context from JWT tokens for use in repositories.
 * Install this inside authenticate { } so it runs after authentication.
 */
val UserContext = createRouteScopedPlugin(name = "UserContextPlugin") {
    on(AuthenticationChecked) { call ->
        try {
            // Try to extract userId from JWT after authentication completes
            val jwtPrincipal = call.principal<JWTPrincipal>()
            val combinedPrincipal = call.principal<CombinedPrincipal>()

            val userId = when {
                combinedPrincipal != null -> {
                    val id = UUID.fromString(combinedPrincipal.jwtPrincipal.subject)
                    log.info("Extracted userId from CombinedPrincipal: $id from ${call.request.local.uri}")
                    id
                }
                jwtPrincipal != null -> {
                    val id = jwtPrincipal.subject?.let { UUID.fromString(it) }
                    log.info("Extracted userId from JWTPrincipal: $id from ${call.request.local.uri}")
                    id
                }
                else -> {
                    log.warn("No principal found, userId is null from ${call.request.local.uri}")
                    null
                }
            }

            BaseRepository.setUserContext(UserContext(userId))
        } catch (e: Exception) {
            // If parsing fails, set anonymous context
            log.error("Exception parsing userId: ${e.message}, setting anonymous context")
            BaseRepository.setUserContext(UserContext(null))
        }
    }

    onCallRespond { _, _ ->
        log.info("Clearing UserContext")
        BaseRepository.clearUserContext()
    }
}