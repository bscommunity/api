package org.bscm.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import org.bscm.clients.applicationHttpClient
import org.bscm.models.interfaces.*
import org.bscm.routes.*
import org.bscm.services.*
import org.bscm.services.auth.DiscordOAuthService
import org.bscm.services.auth.GoogleOAuthService
import org.bscm.services.auth.JWTService
import org.koin.ktor.ext.inject

// Disclaimer: Dependency Injection can't be made inside 'routing { }' block

fun Application.configureRouting() {
    val discordOAuthService: DiscordOAuthService by inject()
    val googleOAuthService: GoogleOAuthService by inject()

    val refreshService by inject<RefreshService>()
    val uploadService by inject<UploadService>()
    val supportUploadService by inject<UploadService>(qualifier = org.koin.core.qualifier.named("support"))
    val jwtService by inject<JWTService>()

    val userRepository by inject<IUserRepository>()
    val chartRepository by inject<IChartRepository>()
    val contributorRepository by inject<IContributorRepository>()
    val knownIssueRepository by inject<IChangelogRepository>()
    val versionRepository by inject<IVersionRepository>()
    val tourPassRepository by inject<ITourPassRepository>()
    val themeRepository by inject<IThemeRepository>()
    val collectionService by inject<CollectionService>()
    val activityRepository by inject<IActivityRepository>()
    val profileService by inject<ProfileService>()

    val mediaInfoService by inject<MediaInfoService>()
    // val previewService by inject<PreviewService>()

    routing {
        // openAPI(path = "docs", swaggerFile = "openapi/documentation.yaml")
        /*openAPI(path = "docs") {
            info = OpenApiInfo("bscm API", "1.0")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }*/

        swaggerUI("/docs") {
            info = OpenApiInfo("bscm API", "1.0")
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }

        // ignore!
        staticResources("/static", "static") // eg. `/static/index.html`

        // ignore!
        get("/") {
            // call.respondText("Hello to the bscm API!")
            call.respondRedirect("/docs")
        }

        // Health check endpoint
        get("/health") {
            call.respond(HttpStatusCode.OK)
        }

        // Status endpoint that proxies to an external service (e.g. for uptime monitoring)
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
        userRoutes(userRepository, profileService, collectionService, activityRepository)
        meRoutes(collectionService, profileService, chartRepository)

        chartRoutes(chartRepository, versionRepository, userRepository, uploadService)
        versionRoutes(versionRepository, chartRepository, userRepository, uploadService, /*supportUploadService*/)
        contributorRoutes(contributorRepository)
        tourPassRoutes(tourPassRepository)
        themeRoutes(themeRepository)
        debugRoutes(mediaInfoService, refreshService, jwtService, chartRepository)
        collectionRoutes(collectionService)
        // changelogRoutes(knownIssueRepository)

        // Discord interactions (slash commands, buttons, etc.)
        interactionsRoutes(
            application.environment.config.propertyOrNull("discord.publicKey")?.getString(),
        )
    }
}
