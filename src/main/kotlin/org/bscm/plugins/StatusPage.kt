package org.bscm.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.Clock

@Serializable
data class ErrorResponse(
    val message: String,
    val code: String,
    val timestamp: String = Clock.System.now().toString(),
    val path: String? = null,
    val details: Map<String, String>? = null
)

class UnauthorizedException(message: String) : Exception(message)

fun Application.configureStatusPages() {
    val logger: org.slf4j.Logger = LoggerFactory.getLogger("StatusPages")

    install(StatusPages) {
        status(HttpStatusCode.TooManyRequests) { call, status ->
            val errorResponse = ErrorResponse(
                message = "Too many requests. Please slow down.",
                code = "RATE_LIMIT_EXCEEDED",
                path = call.request.path()
            )
            call.respond(status, errorResponse)
        }

        exception<Throwable> { call, cause ->
            logger.error("Request failed: ${call.request.httpMethod.value} ${call.request.path()}", cause)

            val (status, errorResponse) = when (cause) {
                is NotImplementedError -> HttpStatusCode.NotImplemented to ErrorResponse(
                    message = cause.message ?: "This feature is not implemented",
                    code = "NOT_IMPLEMENTED",
                    path = call.request.path()
                )

                is BadRequestException -> HttpStatusCode.BadRequest to ErrorResponse(
                    message = cause.message ?: "Invalid request",
                    code = "BAD_REQUEST",
                    path = call.request.path()
                )

                is IllegalArgumentException -> HttpStatusCode.BadRequest to ErrorResponse(
                    message = cause.message ?: "Invalid argument provided",
                    code = "INVALID_ARGUMENT",
                    path = call.request.path()
                )

                is NotFoundException -> HttpStatusCode.NotFound to ErrorResponse(
                    message = cause.message ?: "Resource not found",
                    code = "NOT_FOUND",
                    path = call.request.path()
                )

                is NoSuchElementException -> HttpStatusCode.NotFound to ErrorResponse(
                    message = cause.message ?: "Resource not found",
                    code = "RESOURCE_NOT_FOUND",
                    path = call.request.path()
                )

                is SecurityException -> HttpStatusCode.Forbidden to ErrorResponse(
                    message = cause.message ?: "Access denied",
                    code = "ACCESS_DENIED",
                    path = call.request.path()
                )

                // Handle JWT/Authentication specific exceptions if you use them
                is UnauthorizedException -> HttpStatusCode.Unauthorized to ErrorResponse(
                    message = cause.message ?: "Unauthorized access",
                    code = "UNAUTHORIZED",
                    path = call.request.path()
                )

                else -> {
                    logger.error("Unhandled exception: ${cause::class.simpleName}", cause)
                    HttpStatusCode.InternalServerError to ErrorResponse(
                        message = cause.message ?: "Internal server error",
                        code = "INTERNAL_ERROR",
                        path = call.request.path()
                    )
                }
            }

            call.respond(status, errorResponse)
        }
    }
}