package org.bscm.services.track.resolvers

import io.ktor.client.*
import io.ktor.client.engine.cio.*
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
}