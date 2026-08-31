package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.bscm.models.interfaces.*
import org.bscm.repository.*
import org.bscm.services.*
import org.bscm.services.auth.DiscordOAuthService
import org.bscm.services.auth.GoogleOAuthService
import org.bscm.services.auth.HMACService
import org.bscm.services.auth.JWTService
import org.bscm.services.preview.resolvers.DeezerPreviewResolver
import org.bscm.services.preview.resolvers.ItunesPreviewResolver
import org.bscm.services.preview.resolvers.PreviewResolverRegistry
import org.bscm.services.publish.ChartPublishService
import org.bscm.services.publish.PublishEventService
import org.bscm.services.publish.ThemePublishService
import org.bscm.services.publish.TourPassPublishService
import org.bscm.services.track.TrackInfoService
import org.bscm.services.track.clients.*
import org.bscm.storage.S3StorageAdapter
import org.bscm.storage.StaticUrlStorageAdapter
import org.bscm.storage.StorageService
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDI() {
    val config = environment.config

    // Keep lightweight tests working when external-service configuration is absent.
    val requiredKeys = listOf(
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
    single {
        MusicLinkClient(
            client = get(),
            json = get(),
            apiKey = config.propertyOrNull("musiclink.apiKey")?.getString()
        )
    }

    // Repositories
    single {
        val assetsConfig = config.config("assets")
        val publicBucket = assetsConfig.propertyOrNull("publicBucket")?.getString() ?: "public"
        val publicBaseUrl = assetsConfig.propertyOrNull("publicBaseUrl")?.getString() ?: "https://bscm-assets.s3.amazonaws.com"

        val adapter = if (assetsConfig.propertyOrNull("s3.endpoint") != null &&
            assetsConfig.propertyOrNull("s3.accessKey") != null &&
            assetsConfig.propertyOrNull("s3.secretKey") != null
        ) {
            S3StorageAdapter(
                endpoint = assetsConfig.property("s3.endpoint").getString(),
                accessKey = assetsConfig.property("s3.accessKey").getString(),
                secretKey = assetsConfig.property("s3.secretKey").getString(),
                region = assetsConfig.propertyOrNull("s3.region")?.getString() ?: "us-east-1",
                publicBaseUrl = publicBaseUrl,
            )
        } else {
            StaticUrlStorageAdapter(publicBaseUrl = publicBaseUrl)
        }

        StorageService(
            adapter = adapter,
            publicBucket = publicBucket,
        )
    }

    single { CatalogItemRepository() }
    single { AlbumRepository() }
    single { TrackRepository(storageService = get()) }
    single { BundleUrlCacheRepository() }
    single<IChartRepository> { ChartRepository(get(), get(), get(), get()) }
    single<IContributorRepository> { ContributorRepository() }
    single<IVersionRepository> { VersionRepository() }
    single<ICollectionRepository> { CollectionRepository(get(), get(), get(), get(), get()) }
    single<IActivityRepository> { ActivityRepository() }
    single<IUserRepository> { UserRepository(get(), get(), get(), get()) }
    single<ITourPassRepository> { TourPassRepository(get(), get(), get()) }
    single<IThemeRepository> { ThemeRepository(get(), get()) }
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
            guildId = config.property("workshop.guildId").getString(),
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
        AudioPreviewService(
            registry = get(),
            storageService = get(),
            client = get()
        )
    }
    single {
        PublishEventService()
    }
    single {
        ChartPublishService(
            chartRepository = get(),
            uploadService = get(),
            storageService = get(),
            trackInfoService = get(),
            audioPreviewService = get(),
            activityRepository = get(),
            publishEventService = get(),
            albumRepository = get(),
        )
    }
    single {
        TourPassPublishService(
            tourPassRepository = get(),
            chartRepository = get(),
            uploadService = get(),
            storageService = get(),
            activityRepository = get(),
            publishEventService = get(),
        )
    }
    single {
        ThemePublishService(
            themeRepository = get(),
            versionRepository = get(),
            uploadService = get(),
            storageService = get(),
            activityRepository = get(),
            publishEventService = get(),
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
        TrackInfoService(
            itunes = get(),
            deezer = get(),
            lastFm = get(),
            odesli = get(),
            musicbrainz = get(),
            musicLink = get()
        )
    }
    single {
        PreviewResolverRegistry(
            resolvers = listOf(DeezerPreviewResolver(get()), ItunesPreviewResolver(get()))
        )
    }
    single {
        InteractionResponseService(
            botToken = config.property("discord.botToken").getString(),
            applicationId = config.property("discord.clientId").getString(),
        )
    }
    single {
        TrackCleanupService(storageService = get())
    }
}