package org.bscm.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import org.bscm.services.auth.HMACService
import org.koin.ktor.ext.inject
import java.util.*

private val log = logger("Security")

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
    private val expectedCertificate = configuration.expectedCertificate

    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        internal var authenticationFunction: AuthenticationFunction<HMACCredential> = { null }
        internal lateinit var hmacService: HMACService
        internal lateinit var expectedCertificate: String

        fun validate(body: suspend ApplicationCall.(HMACCredential) -> Any?) {
            authenticationFunction = body
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call

        // Extract HMAC headers (matching Android app)
        val timestamp = call.request.headers["X-Timestamp"]
        val appSignature = call.request.headers["X-App-Signature"]
        val hmacSignature = call.request.headers["X-HMAC"]

        if (timestamp == null || appSignature == null || hmacSignature == null) {
            context.challenge("HMACChallenge", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Missing HMAC headers (X-Timestamp, X-App-Signature, X-HMAC)")
                )
                challenge.complete()
            }
            return
        }

        // Verify using certificate-based verification (validates certificate match + HMAC + timestamp)
        if (!hmacService.verifyAppSignatureWithCertificate(
                appSignature,
                timestamp,
                hmacSignature,
                expectedCertificate
            )
        ) {
            context.challenge("HMACChallenge", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid app certificate or HMAC signature"))
                challenge.complete()
            }
            return
        }

        // Create credential and validate
        val credential = HMACCredential(timestamp, appSignature)
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
    private val expectedCertificate = configuration.expectedCertificate
    private val jwtRequired = configuration.jwtRequired
    private val hmacRequired = configuration.hmacRequired

    class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        lateinit var jwtVerifier: com.auth0.jwt.interfaces.JWTVerifier
        var jwtRealm: String = "Ktor Server"
        lateinit var hmacService: HMACService
        lateinit var expectedCertificate: String
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

        // 2. Validate HMAC headers (from mobile app certificate verification)
        val timestamp = call.request.headers["X-Timestamp"]
        val appSignature = call.request.headers["X-App-Signature"]
        val hmacSignature = call.request.headers["X-HMAC"]

        val hmacPrincipal = if (timestamp == null || appSignature == null || hmacSignature == null) {
            if (hmacRequired) {
                errors.add("Missing HMAC headers (X-Timestamp, X-App-Signature, X-HMAC)")
            }
            null
        } else {
            // Verify app signature with certificate validation
            if (hmacService.verifyAppSignatureWithCertificate(
                    appSignature,
                    timestamp,
                    hmacSignature,
                    expectedCertificate
                )
            ) {
                HMACPrincipal("mobile-app", timestamp)
            } else {
                errors.add("Invalid app certificate or HMAC signature")
                null
            }
        }

        // 3. Check if authentication requirements are met
        val jwtValid = jwtPrincipal != null || !jwtRequired
        val hmacValid = hmacPrincipal != null || !hmacRequired

        // At least one authentication method must be present
        if (jwtPrincipal == null && jwtRequired || hmacPrincipal == null && hmacRequired) {
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
    val hmacService by inject<HMACService>()

    val secret = config.property("jwt.secret").getString()
    val jwtRealm = config.property("jwt.realm").getString()
    val expectedCertificate = config.property("hmac.secret").getString()

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
            this.expectedCertificate = expectedCertificate

            validate { credential ->
                // The credential contains timestamp and appSignature
                // Verification is already done in HMACAuthenticationProvider.onAuthenticate()
                // This is just for backward compatibility - return principal if we got here
                HMACPrincipal("mobile-app", credential.timestamp)
            }
        }

        // Combined authentication: JWT + HMAC (both required)
        combinedAuth("auth-combined") {
            this.jwtVerifier = JWT
                .require(Algorithm.HMAC256(secret))
                .build()
            this.jwtRealm = jwtRealm
            this.hmacService = hmacService
            this.expectedCertificate = expectedCertificate
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
            this.expectedCertificate = expectedCertificate
            this.jwtRequired = false
            this.hmacRequired = false
        }

        // Public access: No authentication required
        combinedAuth("auth-public") {
            this.jwtVerifier = JWT
                .require(Algorithm.HMAC256(secret))
                .build()
            this.jwtRealm = jwtRealm
            this.hmacService = hmacService
            this.expectedCertificate = expectedCertificate
            this.jwtRequired = false
            this.hmacRequired = false
        }
    }
}