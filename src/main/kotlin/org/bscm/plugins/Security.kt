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
import kotlin.math.abs


// Custom principal for HMAC authentication
data class HMACPrincipal(val appId: String, val timestamp: String)

// Custom credential for HMAC
data class HMACCredential(val timestamp: String, val signature: String)

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

// Extension function to register HMAC authentication
fun AuthenticationConfig.hmac(
    name: String? = null,
    configure: HMACAuthenticationProvider.Config.() -> Unit
) {
    val provider = HMACAuthenticationProvider(HMACAuthenticationProvider.Config(name).apply(configure))
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
        jwt("auth-jwt") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .build()
            )
            validate { credential ->
                val userId = credential.subject?.let { jwtService.verifyAccessToken(it) }
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
    }
}