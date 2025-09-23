package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimit() {
    install(RateLimit) {
        register(RateLimitName("restricted")) {
            rateLimiter(limit = 30, refillPeriod = 60.seconds)
        }
        register(RateLimitName("unrestricted")) {
            rateLimiter(limit = 30, refillPeriod = 30.seconds)
        }
    }
}