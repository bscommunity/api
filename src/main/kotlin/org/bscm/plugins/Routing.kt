package org.bscm.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.repository.*
import org.bscm.routes.*
import org.bscm.services.*
import org.koin.ktor.ext.inject

// Disclaimer: Dependency Injection can't be made inside 'routing { }' block

fun Application.configureRouting() {
    val discordOAuthService: DiscordOAuthService by inject()
    val googleOAuthService: GoogleOAuthService by inject()

    val uploadService by inject<UploadService>()
    val jwtService by inject<JWTService>()

    val userRepository by inject<IUserRepository>()
    val chartRepository by inject<IChartRepository>()
    val contributorRepository by inject<IContributorRepository>()
    val knownIssueRepository by inject<IKnownIssueRepository>()
    val versionRepository by inject<IVersionRepository>()
    val tourPassRepository by inject<ITourPassRepository>()
    val themeRepository by inject<IThemeRepository>()
    val collectionService by inject<CollectionService>()

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
        tourPassRoutes(tourPassRepository)
        themeRoutes(themeRepository)
        collectionRoutes(collectionService)

        // Discord interactions (slash commands, buttons, etc.)
        interactionsRoutes(application.environment.config.propertyOrNull("discord.publicKey")?.getString())
    }
}
