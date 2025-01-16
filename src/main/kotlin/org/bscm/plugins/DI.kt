package org.bscm.plugins

import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import io.ktor.server.application.*
import org.bscm.repository.UserRepository
import org.bscm.repository.UserRepositoryImpl

fun Application.configureDI() {
    install(Koin) {
        modules(mainModule)
    }
}

val mainModule = module {
    single { UserRepositoryImpl() as UserRepository }
}