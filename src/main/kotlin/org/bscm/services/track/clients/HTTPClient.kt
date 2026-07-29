package org.bscm.services.track.clients

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

val jsonClient = Json {
    ignoreUnknownKeys = true
}

val applicationHttpClient = HttpClient(CIO) {
    expectSuccess = true
    install(ContentNegotiation) {
        json(jsonClient)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
}