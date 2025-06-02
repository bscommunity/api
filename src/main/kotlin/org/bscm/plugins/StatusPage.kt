package org.bscm.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

fun Application.configureStatusPages() {
    val logger: org.slf4j.Logger = LoggerFactory.getLogger("StatusPages")

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            // call.respondText(text = "404: Page Not Found", status = status)
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "Page not found"))
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            // val retryAfter = call.response.headers["Retry-After"]
            // call.respondText(text = "Whoa there! You're going way too fast \uD83D\uDEA6. Try again in $retryAfter seconds.", status = status)
            call.respond(HttpStatusCode.TooManyRequests, mapOf("message" to "Too many requests. Please slow down."))
        }
        exception<Throwable> { call, cause ->
            logger.error("An unexpected error occurred", cause)
            cause.printStackTrace()

            when (cause) {
                is NotImplementedError -> call.respond(HttpStatusCode.NotImplemented)
                is BadRequestException -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
                is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
                is NotFoundException -> call.respond(HttpStatusCode.NotFound, mapOf("message" to cause.message))
                is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, mapOf("message" to "Resource not found"))
                is SecurityException -> call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Access denied"))
                else -> call.respond(HttpStatusCode.InternalServerError, mapOf("message" to cause.message.let { cause.message ?: "Internal server error" }))
            }
        }
    }
}