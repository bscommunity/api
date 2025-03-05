package org.bscm.plugins

import io.ktor.server.application.*
import org.bscm.repository.ChartRepository
import org.bscm.repository.ContributorRepository
import org.bscm.repository.UserRepository
import org.bscm.repository.VersionRepository
import org.bscm.repository.implementation.ChartRepositoryImpl
import org.bscm.repository.implementation.ContributorRepositoryImpl
import org.bscm.repository.implementation.UserRepositoryImpl
import org.bscm.repository.implementation.VersionRepositoryImpl
import org.bscm.services.JWTService
import org.bscm.services.OAuthService
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
    single<VersionRepository> { VersionRepositoryImpl() }
    single { JWTService(
        secret = System.getenv("JWT_SECRET")
    ) }
    single { OAuthService(
        clientId = System.getenv("DISCORD_CLIENT_ID"),
        clientSecret = System.getenv("DISCORD_CLIENT_SECRET"),
        redirectUri = System.getenv("DISCORD_REDIRECT_URI")
    ) }
}