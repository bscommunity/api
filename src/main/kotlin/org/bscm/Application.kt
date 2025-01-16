package org.bscm

import io.ktor.server.application.*
import org.bscm.plugins.*
import org.bscm.repository.UserRepositoryImpl

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Initialize the database
    configureDatabases(environment.config)

    val repository = UserRepositoryImpl()

    // Plugins
    // configureDI()
    configureSerialization()
    configureHTTP()
    configureStatusPages()
    configureRouting(repository)
}
