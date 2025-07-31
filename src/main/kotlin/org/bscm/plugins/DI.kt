package org.bscm.plugins

import io.ktor.server.application.*
import org.bscm.repository.*
import org.bscm.repository.implementation.*
import org.bscm.services.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDI() {
    install(Koin) {
        slf4jLogger()
        modules(mainModule)
    }
}

val mainModule = module {
    single<UserRepository> { UserRepositoryImpl() }
    single<ChartRepository> { ChartRepositoryImpl() }
    single<ContributorRepository> { ContributorRepositoryImpl() }
    single<KnownIssueRepository> { KnownIssueRepositoryImpl() }
    single<VersionRepository> { VersionRepositoryImpl() }
    single { JWTService(
        secret = System.getenv("JWT_SECRET")
    ) }
    single { HMACService(
        secret = System.getenv("HMAC_SECRET")
    ) }
    single { DiscordOAuthService(
        clientId = System.getenv("DISCORD_CLIENT_ID"),
        clientSecret = System.getenv("DISCORD_CLIENT_SECRET"),
        redirectUri = System.getenv("DISCORD_REDIRECT_URI")
    ) }
    single { GoogleOAuthService(
        clientId = System.getenv("GOOGLE_CLIENT_ID"),
        clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
        redirectUri = System.getenv("GOOGLE_REDIRECT_URI")
    ) }
    single { UploadService(
        webhookId = System.getenv("DISCORD_WEBHOOK_ID"),
        webhookToken = System.getenv("DISCORD_WEBHOOK_TOKEN"),
    ) }
}