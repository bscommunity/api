package org.bscm.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(text = "404: Page Not Found", status = status)
        }
        exception<Throwable> { call, cause ->
            when (cause) {
                is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
                is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Resource not found"))
                is SecurityException -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                else -> call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}