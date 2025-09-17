package org.bscm.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.repository.*
import org.bscm.routes.*
import org.bscm.services.DiscordOAuthService
import org.bscm.services.GoogleOAuthService
import org.bscm.services.JWTService
import org.bscm.services.UploadService
import org.koin.ktor.ext.inject

// Disclaimer: Dependency Injection can't be made inside 'routing { }' block

fun Application.configureRouting() {
    val discordOAuthService: DiscordOAuthService by inject()
    val googleOAuthService: GoogleOAuthService by inject()

    val uploadService by inject<UploadService>()
    val jwtService by inject<JWTService>()

    val userRepository by inject<UserRepository>()
    val chartRepository by inject<ChartRepository>()
    val contributorRepository by inject<ContributorRepository>()
    val knownIssueRepository by inject<KnownIssueRepository>()
    val versionRepository by inject<VersionRepository>()
    val tourPassRepository by inject<TourPassRepository>()
    val themeRepository by inject<ThemeRepository>()
    val userInteractionRepository by inject<UserInteractionRepository>()

    routing {
        swaggerUI(path = "docs", swaggerFile = "openapi/documentation.yaml")
        staticResources("/static", "static") // eg. `/static/index.html`

        get("/") {
            call.respondText("Hello to the bscm API!")
        }

        get("/health") {
            call.respond(HttpStatusCode.OK)
        }

        get("/status") {
            val statusUrl = application.environment.config.propertyOrNull("status.url")?.getString()

            if (statusUrl.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotImplemented, "status.url is not configured")
                return@get
            }

            applicationHttpClient.get(statusUrl)
                .let { call.respondText(it.bodyAsText(), ContentType.Application.Json) }
        }

        authRoutes(userRepository, discordOAuthService, googleOAuthService, jwtService)
        userRoutes(userRepository)

        chartRoutes(chartRepository, userRepository, versionRepository, uploadService)
        versionRoutes(versionRepository, chartRepository, userRepository, uploadService)
        contributorRoutes(contributorRepository)
        knownIssuesRoutes(knownIssueRepository)
        tourPassRoutes(tourPassRepository, userRepository)
        themeRoutes(themeRepository, userRepository)
        userInteractionRoutes(userInteractionRepository)
        interactionsRoutes(application.environment.config.propertyOrNull("discord.publicKey")?.getString())
    }
}
