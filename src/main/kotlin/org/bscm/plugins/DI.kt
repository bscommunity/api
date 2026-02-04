package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import org.bscm.clients.*
import org.bscm.models.dto.PreviewResponse
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
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDI() {
    val config = environment.config
    install(Koin) {
        slf4jLogger()
        modules(mainModule(config))
    }
}

fun mainModule(config: ApplicationConfig) = module {
    // HTTP Client & JSON
    single { applicationHttpClient }
    single { jsonClient }

    val uri = RedisURI.Builder
        .redis(config.property("redis.host").getString(), config.property("redis.port").getString().toInt())
        .withAuthentication("default", config.property("redis.password").getString())
        .build()

    // Redis
    single {
        RedisClient.create(uri)
    }

    // Cache Repository for PreviewResponse
    single<CacheRepository<PreviewResponse>> {
        RedisCacheRepository(
            redisClient = get(),
            json = get(),
            serializer = PreviewResponse.serializer()
        )
    }

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
    single<IChartRepository> { ChartRepository() }
    single<IContributorRepository> { ContributorRepository() }
    single<IKnownIssueRepository> { KnownIssueRepository() }
    single<IVersionRepository> { VersionRepository() }
    single<ITourPassRepository> { TourPassRepository(get()) }
    single<IThemeRepository> { ThemeRepository() }
    single<ICollectionRepository> { CollectionRepository(get(), get(), get()) }
    single<IActivityRepository> { ActivityRepository() }
    single<IUserRepository> { UserRepository(get(), get(), get(), get()) }
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
    single(qualifier = named("support")) {
        UploadService(
            webhookId = config.property("support.webhookId").getString(),
            webhookToken = config.property("support.webhookToken").getString(),
            botToken = config.property("discord.botToken").getString(),
            channelId = config.property("support.channelId").getString(),
        )
    }
    single {
        ChartPublishService(
            chartRepository = get(),
            uploadService = get(),
            // supportUploadService = get(qualifier = named("support")),
            mediaInfoService = get(),
            activityRepository = get()
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
            chartRepository = get()
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