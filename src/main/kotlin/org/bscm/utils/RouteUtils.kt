package org.bscm.utils

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.bscm.models.enums.ContentType
import org.bscm.plugins.UnauthorizedException
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.util.*

/**
 * Extracts the authenticated user's ID from the JWT principal.
 * @throws UnauthorizedException if the user is not authenticated
 */
fun ApplicationCall.getUserId(): UUID {
    val principal = principal<JWTPrincipal>()
    return principal?.subject?.let { UUID.fromString(it) } ?: throw UnauthorizedException("User not authenticated")
}

/**
 * Extracts a ContentType from query parameters if present.
 * @return ContentType enum value or null if not present or invalid
 */
fun ApplicationCall.getContentTypeOrNull(): ContentType? {
    val typeParam = request.queryParameters["contentType"]
    return typeParam?.let {
        try {
            ContentType.valueOf(it.uppercase())
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/**
 * Extracts a UUID from path parameters.
 * @param paramName The name of the parameter (default: "id")
 * @return The UUID value
 * @throws IllegalArgumentException if the parameter is missing or invalid
 */
fun ApplicationCall.getId(paramName: String = "id"): UUID {
    val idString = parameters[paramName] ?: throw IllegalArgumentException("Invalid or missing $paramName")
    return try {
        UUID.fromString(idString)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("$paramName must be a valid UUID")
    }
}

/**
 * Extracts a content ID (String) from path parameters.
 * @param paramName The name of the parameter (default: "itemId")
 * @return The content ID string
 * @throws IllegalArgumentException if the parameter is missing
 */
fun ApplicationCall.getContentId(paramName: String = "itemId"): String {
    return parameters[paramName] ?: throw IllegalArgumentException("Invalid or missing $paramName")
}

/**
 * Extracts pagination parameters with optional limit coercion and nullable offset.
 * @param coerceLimit Maximum allowed limit value (optional)
 * @param defaultOffset Default offset value if not provided (default: null)
 * @return Pair of limit (nullable) and offset (nullable or default)
 */
fun ApplicationCall.getPagination(coerceLimit: Int? = null, defaultOffset: Int? = null): Pair<Int?, Int?> {
    val limit = request.queryParameters["limit"]?.toIntOrNull().let {
        if (coerceLimit != null) {
            it?.coerceAtMost(coerceLimit)
        } else {
            it
        }
    }
    val offset = request.queryParameters["offset"]?.toIntOrNull() ?: defaultOffset
    return limit to offset
}

suspend fun <T> retryOnConflict(
    maxAttempts: Int = 3,
    block: suspend () -> T
): T {
    repeat(maxAttempts - 1) {
        try {
            return block()
        } catch (e: ExposedSQLException) {
            if (!e.message.orEmpty().contains("unique constraint", ignoreCase = true)) throw e
            // shareId collision, retry with a freshly generated one
        }
    }
    return block() // last attempt, let it throw naturally
}