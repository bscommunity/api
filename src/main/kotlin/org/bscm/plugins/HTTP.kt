package org.bscm.plugins

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
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

fun Application.configureHTTP() {
    install(CORS) {
        allowCredentials = true
        allowNonSimpleContentTypes = true

        anyHost()

        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)

        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
}
