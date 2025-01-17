package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.repository.UserRepository
import org.bscm.repository.UserRepositoryImpl
import org.bscm.routes.userRoutes

fun Application.configureRouting() {
    val userRepository = UserRepositoryImpl()

    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        staticResources("/static", "static") // eg. `/static/index.html`

        get("/") {
            call.respondText("Hello World!")
        }

        userRoutes(userRepository)
    }
}
