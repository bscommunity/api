package org.bscm.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

class JWTService(
    secret: String,
) {
    // The issuer and audience are used to validate the token
    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(userId: UUID): String {
        return JWT.create()
            .withSubject(userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)) // 24 hours
            .sign(algorithm)
    }

    fun verifyToken(userId: String): UUID? {
        return UUID.fromString(userId)
        /*return try {
            val decodedJWT = JWT.require(algorithm)
                .build()
                .verify(token)
            UUID.fromString(decodedJWT.subject)
        } catch (e: Exception) {
            null
        }*/
    }
}