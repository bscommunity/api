package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.bscm.clients.*
import org.bscm.models.interfaces.*
import org.bscm.repository.*
import org.bscm.services.*
import org.bscm.services.auth.DiscordOAuthService
import org.bscm.services.auth.GoogleOAuthService
import org.bscm.services.auth.HMACService
import org.bscm.services.auth.JWTService
import org.bscm.services.preview.PreviewService
import org.bscm.services.preview.resolvers.DeezerPreviewResolver
import org.bscm.services.preview.resolvers.ItunesPreviewResolver
import org.bscm.services.preview.resolvers.PreviewResolverRegistry
import org.bscm.storage.StaticUrlStorageAdapter
import org.bscm.storage.StorageService
import org.bscm.storage.SupabaseStorageAdapter
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDI() {
    val config = environment.config

    // Keep lightweight tests working when external-service configuration is absent.
    val requiredKeys = listOf(
        "redis.host",
        "redis.port",
        "redis.password",
        "jwt.secret",
        "discord.clientId",
        "discord.clientSecret",
        "discord.redirectUri",
        "discord.botToken",
        "workshop.webhookId",
        "workshop.webhookToken",
        "workshop.channelId",
        "lastfm.apiKey",
        "google.clientId",
        "google.clientSecret",
        "google.redirectUri",
    )

    if (requiredKeys.any { config.propertyOrNull(it) == null }) {
        log.warn("Skipping DI/Koin installation: external service configuration is incomplete")
        return
    }

    install(Koin) {
        slf4jLogger()
        modules(mainModule(config))
    }
}

fun mainModule(config: ApplicationConfig) = module {
    // HTTP Client & JSON
    single { applicationHttpClient }
    single { jsonClient }

    // API Clients
    single { DeezerClient(client = get(), json = get()) }
    single { ItunesClient(client = get(), json = get()) }
    single {
        LastFmClient(
            apiKey = config.property("lastfm.apiKey").getString(),
            client = get(),
            json = get()
        )
    }
    single { OdesliClient(client = get(), json = get()) }
    single { MusicbrainzClient(client = get(), json = get()) }

    // Repositories
    single {
        val assetsConfig = config.config("assets")
        val publicBucket = assetsConfig.propertyOrNull("publicBucket")?.getString() ?: "public"
        val publicBaseUrl = assetsConfig.propertyOrNull("publicBaseUrl")?.getString()

        val adapter = if (assetsConfig.propertyOrNull("supabase.url") != null &&
            assetsConfig.propertyOrNull("supabase.serviceKey") != null
        ) {
            SupabaseStorageAdapter(
                client = get(),
                baseUrl = assetsConfig.property("supabase.url").getString(),
                serviceKey = assetsConfig.property("supabase.serviceKey").getString(),
                publicBaseUrl = publicBaseUrl,
            )
        } else {
            val fallbackUrl = publicBaseUrl ?: "https://bscm-assets.s3.amazonaws.com"
            StaticUrlStorageAdapter(publicBaseUrl = fallbackUrl)
        }

        StorageService(
            adapter = adapter,
            publicBucket = publicBucket,
        )
    }

    single { CatalogItemRepository() }
    single { TrackRepository(storageService = get()) }
    single { BundleUrlCacheRepository() }
    single<IChartRepository> { ChartRepository(get(), get(), get()) }
    single<IContributorRepository> { ContributorRepository() }
    single<IVersionRepository> { VersionRepository() }
    single<ICollectionRepository> { CollectionRepository(get(), get(), get(), get()) }
    single<IActivityRepository> { ActivityRepository() }
    single<IUserRepository> { UserRepository(get(), get(), get(), get()) }
    single<ITourPassRepository> { TourPassRepository(get()) }
    single<IThemeRepository> { ThemeRepository() }
    single<IChangelogRepository> { ChangelogRepository() }
    single {
        JWTService(
            secret = config.property("jwt.secret").getString()
        )
    }
    single {
        HMACService()
    }
    single {
        DiscordOAuthService(
            clientId = config.property("discord.clientId").getString(),
            clientSecret = config.property("discord.clientSecret").getString(),
            redirectUri = config.property("discord.redirectUri").getString()
        )
    }
    single {
        GoogleOAuthService(
            clientId = config.property("google.clientId").getString(),
            clientSecret = config.property("google.clientSecret").getString(),
            redirectUri = config.property("google.redirectUri").getString()
        )
    }
    single {
        UploadService(
            webhookId = config.property("workshop.webhookId").getString(),
            webhookToken = config.property("workshop.webhookToken").getString(),
            botToken = config.property("discord.botToken").getString(),
            channelId = config.property("workshop.channelId").getString(),
        )
    }
    single {
        RefreshService(
            botToken = config.property("discord.botToken").getString(),
            channelId = config.property("workshop.channelId").getString(),
        )
    }
    single {
        BundleDownloadService(
            cacheRepository = get(),
            client = get(),
            botToken = config.property("discord.botToken").getString(),
            channelId = config.property("workshop.channelId").getString(),
        )
    }
    single {
        ChartPublishService(
            chartRepository = get(),
            uploadService = get(),
            mediaInfoService = get(),
            activityRepository = get()
        )
    }
    single {
        TourPassPublishService(
            tourPassRepository = get(),
            chartRepository = get(),
            uploadService = get(),
            activityRepository = get(),
        )
    }
    single {
        ThemePublishService(
            themeRepository = get(),
            uploadService = get(),
            activityRepository = get(),
        )
    }
    single {
        CollectionService(
            collectionRepository = get(),
            activityRepository = get()
        )
    }
    single {
        ProfileService(
            userRepository = get(),
            activityRepository = get(),
            chartRepository = get(),
            tourPassRepository = get(),
            themeRepository = get(),
        )
    }
    single {
        MediaInfoService(
            itunes = get(),
            deezer = get(),
            lastFm = get(),
            odesli = get(),
            musicbrainz = get()
        )
    }
    single {
        PreviewResolverRegistry(
            resolvers = listOf(DeezerPreviewResolver(get()), ItunesPreviewResolver(get()))
        )
    }
    single {
        PreviewService(
            registry = get(),
            cache = get(),
        )
    }
    single {
        InteractionResponseService(
            botToken = config.property("discord.botToken").getString(),
            applicationId = config.property("discord.clientId").getString(),
        )
    }
}