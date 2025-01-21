package org.bscm

import io.ktor.server.application.*
import org.bscm.plugins.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Initialize the database
    configureDatabases(environment.config)

    // Plugins
    configureDI()
    configureSerialization()
    configureHTTP()
    configureStatusPages()
    configureSecurity(environment.config)
    configureRouting()
}
