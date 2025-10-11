package org.bscm.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import org.bscm.services.HMACService
import org.bscm.services.JWTService
import org.koin.ktor.ext.inject
import java.util.*
import kotlin.math.abs

// Custom principal for HMAC authentication
data class HMACPrincipal(val appId: String, val timestamp: String)

// Custom credential for HMAC
data class HMACCredential(val timestamp: String, val signature: String)

// Combined principal that holds both JWT and HMAC principals
data class CombinedPrincipal(
    val jwtPrincipal: JWTPrincipal,
    val hmacPrincipal: HMACPrincipal
)

// Custom authentication provider for HMAC
class HMACAuthenticationProvider internal constructor(
    configuration: Config
) : AuthenticationProvider(configuration) {

    internal val authenticationFunction = configuration.authenticationFunction
    private val hmacService = configuration.hmacService
    private val hmacSecret = configuration.hmacSecret

    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        internal var authenticationFunction: AuthenticationFunction<HMACCredential> = { null }
        internal lateinit var hmacService: HMACService
        internal lateinit var hmacSecret: String

        fun validate(body: suspend ApplicationCall.(HMACCredential) -> Any?) {
            authenticationFunction = body
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call

        // Extract HMAC headers
        val timestamp = call.request.headers["X-App-Timestamp"]
        val signature = call.request.headers["X-App-Signature"]

        if (timestamp == null || signature == null) {
            context.challenge("HMACChallenge", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing HMAC headers"))
                challenge.complete()
            }
            return
        }

        // Verify timestamp is recent (within 5 minutes)
        val currentTime = System.currentTimeMillis()
        val requestTime = timestamp.toLongOrNull()

        if (requestTime == null) {
            context.challenge("HMACChallenge", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid timestamp format"))
                challenge.complete()
            }
            return
        }

        val timeDifference = abs(currentTime - requestTime)
        if (timeDifference > 300_000) { // 5 minutes in milliseconds
            context.challenge("HMACChallenge", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Request timestamp too old"))
                challenge.complete()
            }
            return
        }

        // Create credential and validate
        val credential = HMACCredential(timestamp, signature)
        val principal = call.authenticationFunction(credential)

        if (principal != null) {
            context.principal(principal)
        } else {
            context.challenge("HMACChallenge", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid HMAC signature"))
                challenge.complete()
            }
        }
    }
}

// Custom combined authentication provider for JWT + HMAC
class CombinedAuthenticationProvider internal constructor(
    configuration: Config
) : AuthenticationProvider(configuration) {

    private val jwtVerifier = configuration.jwtVerifier
    private val jwtRealm = configuration.jwtRealm
    private val hmacService = configuration.hmacService
    private val jwtRequired = configuration.jwtRequired
    private val hmacRequired = configuration.hmacRequired

    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        lateinit var jwtVerifier: com.auth0.jwt.interfaces.JWTVerifier
        var jwtRealm: String = "Ktor Server"
        lateinit var hmacService: HMACService
        var jwtRequired: Boolean = true
        var hmacRequired: Boolean = true
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val errors = mutableListOf<String>()

        // 1. Validate JWT from Authorization header
        val authHeader = call.request.headers["Authorization"]
        val token = authHeader?.removePrefix("Bearer ")?.trim()

        val jwtPrincipal = if (token.isNullOrBlank()) {
            if (jwtRequired) {
                errors.add("Missing or invalid Authorization header")
            }
            null
        } else {
            try {
                val payload = jwtVerifier.verify(token)
                val userId = payload.subject?.let { UUID.fromString(it) }
                if (userId != null) {
                    JWTPrincipal(payload)
                } else {
                    errors.add("Invalid JWT subject")
                    null
                }
            } catch (e: Exception) {
                errors.add("JWT verification failed: ${e.message}")
                null
            }
        }

        // 2. Validate HMAC headers
        val timestamp = call.request.headers["X-App-Timestamp"]
        val signature = call.request.headers["X-App-Signature"]

        val hmacPrincipal = if (timestamp == null || signature == null) {
            if (hmacRequired) {
                errors.add("Missing HMAC headers")
            }
            null
        } else {
            // Verify timestamp is recent (within 5 minutes)
            val currentTime = System.currentTimeMillis()
            val requestTime = timestamp.toLongOrNull()

            if (requestTime == null) {
                errors.add("Invalid timestamp format")
                null
            } else {
                val timeDifference = abs(currentTime - requestTime)
                if (timeDifference > 300_000) { // 5 minutes
                    errors.add("Request timestamp too old")
                    null
                } else {
                    // Verify HMAC signature
                    val payload = "$timestamp:"
                    val expectedSignature = hmacService.calculateSignature(payload)

                    if (hmacService.verifySignature(signature, expectedSignature)) {
                        HMACPrincipal("mobile-app", timestamp)
                    } else {
                        errors.add("Invalid HMAC signature")
                        null
                    }
                }
            }
        }

        // 3. Check if authentication requirements are met
        val jwtValid = jwtPrincipal != null || !jwtRequired
        val hmacValid = hmacPrincipal != null || !hmacRequired

        // At least one authentication method must be present
        if (jwtPrincipal == null && hmacPrincipal == null) {
            context.challenge("CombinedAuthChallenge", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("No authentication provided", "401", details = mapOf("errors" to errors.toString()))
                )
                challenge.complete()
            }
            return
        }

        if (jwtValid && hmacValid) {
            // Create combined principal with whatever we have
            if (jwtPrincipal != null && hmacPrincipal != null) {
                context.principal(CombinedPrincipal(jwtPrincipal, hmacPrincipal))
            } else if (jwtPrincipal != null) {
                context.principal(jwtPrincipal)
            } else if (hmacPrincipal != null) {
                context.principal(hmacPrincipal)
            }
        } else {
            context.challenge(
                "CombinedAuthChallenge",
                AuthenticationFailedCause.InvalidCredentials
            ) { challenge, call ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        "Combined authentication failed",
                        "401",
                        details = mapOf("errors" to errors.toString())
                    )
                )
                challenge.complete()
            }
        }
    }
}

// Extension function to register HMAC authentication
fun AuthenticationConfig.hmac(
    name: String? = null,
    configure: HMACAuthenticationProvider.Config.() -> Unit
) {
    val provider = HMACAuthenticationProvider(HMACAuthenticationProvider.Config(name).apply(configure))
    register(provider)
}

// Extension function to register combined JWT + HMAC authentication
fun AuthenticationConfig.combinedAuth(
    name: String? = null,
    configure: CombinedAuthenticationProvider.Config.() -> Unit
) {
    val provider = CombinedAuthenticationProvider(CombinedAuthenticationProvider.Config(name).apply(configure))
    register(provider)
}

fun Application.configureSecurity(
    config: ApplicationConfig
) {
    val jwtService by inject<JWTService>()
    val hmacService by inject<HMACService>()

    val secret = config.property("jwt.secret").getString()
    val jwtRealm = config.property("jwt.realm").getString()

    val hmacSecret = config.property("hmac.secret").getString()

    install(Authentication) {
        jwt("auth-bearer") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .build()
            )
            validate { credential ->
                val userId = credential.subject?.let { UUID.fromString(it) }
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

        hmac("auth-hmac") {
            this.hmacService = hmacService
            this.hmacSecret = hmacSecret

            validate { credential ->
                // Recreate the payload that should have been signed
                val payload = "${credential.timestamp}:"

                // Calculate expected signature
                val expectedSignature = hmacService.calculateSignature(payload)
                // println("Expected Signature: $expectedSignature")

                // Compare signatures securely
                if (hmacService.verifySignature(credential.signature, expectedSignature)) {
                    HMACPrincipal("mobile-app", credential.timestamp)
                } else {
                    null
                }
            }
        }

        // Combined authentication: JWT + HMAC (both required)
        combinedAuth("auth-combined") {
            this.jwtVerifier = JWT
                .require(Algorithm.HMAC256(secret))
                .build()
            this.jwtRealm = jwtRealm
            this.hmacService = hmacService
            this.jwtRequired = true
            this.hmacRequired = true
        }

        // Flexible authentication: JWT OR HMAC (at least one required)
        combinedAuth("auth-flexible") {
            this.jwtVerifier = JWT
                .require(Algorithm.HMAC256(secret))
                .build()
            this.jwtRealm = jwtRealm
            this.hmacService = hmacService
            this.jwtRequired = false
            this.hmacRequired = false
        }
    }
}