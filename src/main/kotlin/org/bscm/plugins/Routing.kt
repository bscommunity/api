package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.repository.ChartRepository
import org.bscm.repository.UserRepository
import org.bscm.routes.authRoutes
import org.bscm.routes.chartRoutes
import org.bscm.routes.userRoutes
import org.bscm.services.AuthService
import org.koin.ktor.ext.inject

// Disclaimer: Dependency Injection can't be made inside 'routing { }' block

fun Application.configureRouting() {
    val authService by inject<AuthService>()
    val userRepository by inject<UserRepository>()
    val chartRepository by inject<ChartRepository>()

    routing {
        swaggerUI(path = "docs", swaggerFile = "openapi/documentation.yaml")
        staticResources("/static", "static") // eg. `/static/index.html`

        get("/") {
            call.respondText("Hello World!")
        }

        authRoutes(authService)
        chartRoutes(chartRepository)
        userRoutes(userRepository)
    }
}
