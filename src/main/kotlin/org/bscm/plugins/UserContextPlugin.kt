package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.bscm.models.UserContext
import org.bscm.repository.ChartRepository
import java.util.*

val UserContextPlugin = createApplicationPlugin(name = "UserContextPlugin") {
    onCall { call ->
        try {
            // Try to extract userId from JWT
            val jwtPrincipal = call.principal<JWTPrincipal>()
            val combinedPrincipal = call.principal<CombinedPrincipal>()

            val userId = when {
                combinedPrincipal != null -> UUID.fromString(combinedPrincipal.jwtPrincipal.subject)
                jwtPrincipal != null -> jwtPrincipal.subject?.let { UUID.fromString(it) }
                else -> null
            }

            ChartRepository.setUserContext(UserContext(userId))
        } catch (e: Exception) {
            // If parsing fails, set anonymous context
            ChartRepository.setUserContext(UserContext(null))
        }
    }

    onCallRespond { call, _ ->
        ChartRepository.clearUserContext()
    }
}
