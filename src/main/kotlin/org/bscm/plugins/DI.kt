package org.bscm.plugins

import com.typesafe.config.ConfigFactory
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import io.ktor.server.application.*
import org.bscm.repository.UserRepository
import org.bscm.repository.UserRepositoryImpl
import org.bscm.services.AuthService
import org.bscm.services.JWTService
import org.koin.logger.slf4jLogger

fun Application.configureDI() {
    install(Koin) {
        slf4jLogger()
        modules(mainModule)
    }
}

val mainModule = module {
    single<UserRepository> { UserRepositoryImpl() }
    single { JWTService(
        secret = System.getenv("JWT_SECRET")
    ) }
    single { AuthService(get(), get()) }
}