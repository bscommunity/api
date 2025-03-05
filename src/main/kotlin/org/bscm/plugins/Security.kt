package org.bscm.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import org.bscm.services.JWTService
import org.koin.ktor.ext.inject

val redirects = mutableMapOf<String, String>()

fun Application.configureSecurity(
    config: ApplicationConfig
) {
    val jwtService by inject<JWTService>()

    val secret = config.property("jwt.secret").getString()
    val jwtRealm = config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .build()
            )
            validate { credential ->
                println("Credential: $credential")

                val userId = credential.subject?.let { jwtService.verifyToken(it) }
                if (userId != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token is not valid or has expired"))
            }
            realm = jwtRealm
        }
    }
}