package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.repository.ChartRepository
import org.bscm.repository.ContributorRepository
import org.bscm.repository.UserRepository
import org.bscm.repository.VersionRepository
import org.bscm.routes.authRoutes
import org.bscm.routes.chartRoutes
import org.bscm.routes.userRoutes
import org.bscm.services.JWTService
import org.bscm.services.OAuthService
import org.koin.ktor.ext.inject

// Disclaimer: Dependency Injection can't be made inside 'routing { }' block

fun Application.configureRouting() {
    val oAuthService by inject<OAuthService>()
    val userRepository by inject<UserRepository>()
    val jwtService by inject<JWTService>()

    val chartRepository by inject<ChartRepository>()
    val contributorRepository by inject<ContributorRepository>()
    val versionRepository by inject<VersionRepository>()

    routing {
        swaggerUI(path = "docs", swaggerFile = "openapi/documentation.yaml")
        staticResources("/static", "static") // eg. `/static/index.html`

        get("/") {
            call.respondText("Hello World!")
        }

        authRoutes(userRepository, oAuthService, jwtService)
        chartRoutes(chartRepository, contributorRepository, versionRepository)
        userRoutes(userRepository)
    }
}
