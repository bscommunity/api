package org.bscm.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import org.bscm.models.interfaces.*
import org.bscm.routes.*
import org.bscm.services.*
import org.bscm.services.auth.DiscordOAuthService
import org.bscm.services.auth.GoogleOAuthService
import org.bscm.services.auth.JWTService
import org.bscm.services.preview.PreviewService
import org.bscm.services.track.TrackInfoService
import org.bscm.services.track.resolvers.applicationHttpClient
import org.koin.ktor.ext.inject

// Disclaimer: Dependency Injection can't be made inside 'routing { }' block

fun Application.configureRouting() {
    val cfg = environment.config
    val hasFullConfig = listOf(
        /*"redis.host",
        "redis.port",
        "redis.password",*/
        "jwt.secret",
        "hmac.secret",
        "refresh.secret",
        "discord.clientId",
        "discord.clientSecret",
        "discord.redirectUri",
        "discord.botToken",
        "discord.publicKey",
        "workshop.webhookId",
        "workshop.webhookToken",
        "workshop.channelId",
        "lastfm.apiKey",
        "google.clientId",
        "google.clientSecret",
        "google.redirectUri",
    ).all { cfg.propertyOrNull(it) != null }

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

        if (!hasFullConfig) {
            return@routing
        }

        val discordOAuthService: DiscordOAuthService by inject()
        val googleOAuthService: GoogleOAuthService by inject()

        val refreshService by inject<RefreshService>()
        val uploadService by inject<UploadService>()
        val jwtService by inject<JWTService>()

        val userRepository by inject<IUserRepository>()
        val contributorRepository by inject<IContributorRepository>()
        val changelogRepository by inject<IChangelogRepository>()
        val versionRepository by inject<IVersionRepository>()
        val collectionService by inject<CollectionService>()
        val activityRepository by inject<IActivityRepository>()

        val profileService by inject<ProfileService>()
        val bundleDownloadService by inject<BundleDownloadService>()
        val trackInfoService by inject<TrackInfoService>()
        val previewService by inject<PreviewService>()

        val chartRepository by inject<IChartRepository>()
        val tourPassRepository by inject<ITourPassRepository>()
        val themeRepository by inject<IThemeRepository>()

        val chartPublishService by inject<ChartPublishService>()
        val tourPassPublishService by inject<TourPassPublishService>()
        val themePublishService by inject<ThemePublishService>()

        authRoutes(userRepository, discordOAuthService, googleOAuthService, jwtService)
        userRoutes(userRepository, profileService, collectionService, activityRepository)
        meRoutes(collectionService, profileService, chartRepository)

        chartRoutes(
            chartRepository,
            versionRepository,
            userRepository,
            uploadService,
            chartPublishService,
            bundleDownloadService,
        )
        versionRoutes(versionRepository, chartRepository, userRepository, uploadService)
        contributorRoutes(contributorRepository)
        debugRoutes(trackInfoService, refreshService, jwtService, chartRepository)
        collectionRoutes(collectionService)
        changelogRoutes(changelogRepository)

        // Discord interactions (slash commands, buttons, etc.)
        interactionsRoutes(
            application.environment.config.propertyOrNull("discord.publicKey")?.getString(),
        )

        // Internal cleanup endpoint (protected by Bearer token)
        val cleanupSecret = cfg.propertyOrNull("cleanup.secret")?.getString()
        if (!cleanupSecret.isNullOrBlank()) {
            val trackCleanupService by inject<TrackCleanupService>()

            post("/internal/cleanup/tracks") {
                val bearer = call.request.authorization()?.removePrefix("Bearer ")
                if (bearer != cleanupSecret) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val deleted = trackCleanupService.cleanupOrphanedTracks()
                call.respond(mapOf("deleted" to deleted))
            }
        }
    }
}
