package org.bscm.routes

import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.interfaces.IThemeRepository

fun Route.themeRoutes(
    themeRepository: IThemeRepository,
) {

    route("/themes") {
        authenticate("auth-public") {
            rateLimit(RateLimitName("restricted")) {
                get {
                    val search = call.request.queryParameters["query"]
                        ?.takeIf { it.isNotBlank() }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    val count = call.request.queryParameters["count"]?.toBoolean() ?: false

                    val themes = themeRepository.getThemes(
                        search = search,
                        limit = limit,
                        offset = offset
                    )

                    val total = if (count) {
                        themeRepository.getThemes(search = search).size
                    } else null

                    call.respond(
                        if (total != null) Pair(themes, total) else Pair(themes, null)
                    )
                }
            }
        }
    }
}
