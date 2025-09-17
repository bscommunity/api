package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.bscm.repository.*
import org.bscm.repository.implementation.*
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
    single<UserRepository> { UserRepositoryImpl() }
    single<ChartRepository> { ChartRepositoryImpl() }
    single<ContributorRepository> { ContributorRepositoryImpl() }
    single<KnownIssueRepository> { KnownIssueRepositoryImpl() }
    single<VersionRepository> { VersionRepositoryImpl() }
    single<TourPassRepository> { TourPassRepositoryImpl(get()) }
    single<ThemeRepository> { ThemeRepositoryImpl() }
    single<UserCollectionRepository> { UserCollectionRepositoryImpl() }
    single { CollectionService(get()) }
    single { JWTService(
        secret = config.property("jwt.secret").getString()
    ) }
    single { HMACService(
        secret = config.property("hmac.secret").getString()
    ) }
    single { DiscordOAuthService(
        clientId = config.property("discord.clientId").getString(),
        clientSecret = config.property("discord.clientSecret").getString(),
        redirectUri = config.property("discord.redirectUri").getString()
    ) }
    single { GoogleOAuthService(
        clientId = config.property("google.clientId").getString(),
        clientSecret = config.property("google.clientSecret").getString(),
        redirectUri = config.property("google.redirectUri").getString()
    ) }
    single { UploadService(
        webhookId = config.property("discord.webhookId").getString(),
        webhookToken = config.property("discord.webhookToken").getString(),
        botToken = config.property("discord.botToken").getString(),
        channelId = config.property("discord.channelId").getString(),
    ) }
}