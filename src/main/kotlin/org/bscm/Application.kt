package org.bscm

import io.ktor.server.application.*
import org.bscm.plugins.configureDatabases

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Initialize the database
    configureDatabases(environment.config)

    // Plugins
    /*configureDI()
    configureSerialization()
    configureHTTP()
    configureStatusPages()
    configureSecurity(environment.config)
    configureRateLimit()
    configureRouting() // Routing should be the last plugin to be configured

    // Register of Discord commands (guild immediate, global can take up to 1h)
    val cfg = environment.config
    val botToken = cfg.propertyOrNull("discord.botToken")?.getString()
    val clientId = cfg.propertyOrNull("discord.clientId")?.getString()
    val guildId = cfg.propertyOrNull("discord.guildId")?.getString() // optional for testing

    if (!botToken.isNullOrBlank() && !clientId.isNullOrBlank()) {
        registerDiscordCommands(botToken, clientId, guildId)
    } else {
        log.info("[DiscordCmd] Not registered: botToken or clientId missing")
    }*/

    // Optional: Seed the database if needed
    // Seed the database
}
