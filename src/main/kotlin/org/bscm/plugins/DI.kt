package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.bscm.models.repository.*
import org.bscm.repository.*
import org.bscm.services.*
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
    single<IChartRepository> { ChartRepository() }
    single<IContributorRepository> { ContributorRepository() }
    single<IKnownIssueRepository> { KnownIssueRepository() }
    single<IVersionRepository> { VersionRepository() }
    single<ITourPassRepository> { TourPassRepository(get()) }
    single<IThemeRepository> { ThemeRepository() }
    single<ICollectionRepository> { CollectionRepository(get(), get(), get()) }
    single<IUserRepository> { UserRepository(get(), get(), get(), get()) }
    single { CollectionService(get()) }
    single {
        JWTService(
            secret = config.property("jwt.secret").getString()
        )
    }
    single {
        HMACService(
            secret = config.property("hmac.secret").getString()
        )
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
    single(qualifier = org.koin.core.qualifier.named("support")) {
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
            supportUploadService = get(qualifier = org.koin.core.qualifier.named("support")),
            mediaInfoService = get(),
        )
    }
    single {
        MediaInfoService(
            lastfmApiKey = config.property("lastfm.apiKey").getString()
        )
    }
    single {
        InteractionResponseService(
            botToken = config.property("discord.botToken").getString(),
            applicationId = config.property("discord.clientId").getString(),
        )
    }
}