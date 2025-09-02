package org.bscm.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.util.*

class JWTService(
    secret: String,
) {
    private val algorithm = Algorithm.HMAC256(secret)

    companion object {
        private const val ACCESS_TOKEN_EXPIRY = 24 * 60 * 60 * 1000L // 24 hours
        private const val REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val ACCESS_TYPE = "access"
        private const val REFRESH_TYPE = "refresh"
    }

    fun generateAccessToken(userId: UUID): String {
        return JWT.create()
            .withSubject(userId.toString())
            .withClaim("type", ACCESS_TYPE)
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
            .sign(algorithm)
    }

    fun generateRefreshToken(userId: UUID): String {
        return JWT.create()
            .withSubject(userId.toString())
            .withClaim("type", REFRESH_TYPE)
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY))
            .sign(algorithm)
    }

    fun verifyAccessToken(token: String): UUID? {
        return verifyTokenWithType(token, ACCESS_TYPE)
    }

    fun verifyRefreshToken(refreshToken: String): UUID? {
        return verifyTokenWithType(refreshToken, REFRESH_TYPE)
    }

    private fun verifyTokenWithType(token: String, expectedType: String): UUID? {
        return try {
            val decodedJWT = JWT.require(algorithm)
                .withClaim("type", expectedType)
                .build()
                .verify(token)
            UUID.fromString(decodedJWT.subject)
        } catch (e: JWTVerificationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun getAccessTokenExpiresIn(): Long = ACCESS_TOKEN_EXPIRY / 1000
}