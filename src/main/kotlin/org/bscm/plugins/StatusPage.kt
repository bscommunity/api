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
            cause.printStackTrace()

            when (cause) {
                is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
                is NotFoundException -> call.respond(HttpStatusCode.NotFound, mapOf("message" to cause.message))
                is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, mapOf("message" to "Resource not found"))
                is SecurityException -> call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Access denied"))
                else -> call.respond(HttpStatusCode.InternalServerError, mapOf("message" to cause.message.let { cause.message ?: "Internal server error" }))
            }
        }
    }
}