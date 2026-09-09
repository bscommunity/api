package org.bscm.routes

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.services.OverviewService
import org.bscm.utils.getUserId

fun Route.overviewRoutes(overviewService: OverviewService) {
    route("/me") {
        authenticate("auth-bearer") {

            /**
             * Get authenticated user's overview dashboard data.
             *
             * Tag: Me
             *
             * Query: range [String] Date range filter: "7d", "30d", or "all" (default: "30d").
             *
             * Responses:
             *   - 200 application/json [OverviewResponse] Aggregated overview data.
             *   - 401 application/json [Error] User not authenticated.
             *
             * Security: auth-bearer
             */
            get("/overview") {
                val userId = call.getUserId()
                val range = call.request.queryParameters["range"] ?: "30d"

                val response = overviewService.getOverview(userId, range)
                call.respond(response)
            }
        }
    }
}
