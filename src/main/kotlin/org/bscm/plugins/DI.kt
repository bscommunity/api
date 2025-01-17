package org.bscm.plugins

import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import io.ktor.server.application.*
import org.bscm.repository.UserRepository
import org.bscm.repository.UserRepositoryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.logger.slf4jLogger

fun Application.configureDI() {
    install(Koin) {
        slf4jLogger()
        modules(mainModule)
    }
}

val mainModule = module {
    singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
}