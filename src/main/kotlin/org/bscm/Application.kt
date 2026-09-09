package org.bscm

import io.ktor.server.application.*
import io.ktor.server.sse.*
import org.bscm.plugins.*
import org.bscm.utils.CommandUtils.registerDiscordCommands

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val cfg = environment.config

    fun hasAll(vararg keys: String): Boolean = keys.all { cfg.propertyOrNull(it) != null }

    // Initialize the database only when the runtime config is complete.
    if (hasAll("storage.jdbcURL", "storage.user", "storage.password")) {
        configureDatabases(cfg)
    } else {
        log.warn("Skipping database initialization: storage config is incomplete")
    }

    // Plugins
    configureDI()
    configureSerialization()
    configureHTTP()
    configureStatusPages()
    install(SSE)
    if (hasAll("jwt.secret", "hmac.secret", "refresh.secret")) {
        configureSecurity(cfg)
    } else {
        log.warn("Skipping security setup: JWT/HMAC/refresh config is incomplete")
    }
    configureRateLimit()
    configureRouting() // Routing should be the last plugin to be configured

    // Register of Discord commands (guild immediate, global can take up to 1h)
    val botToken = cfg.propertyOrNull("discord.botToken")?.getString()
    val clientId = cfg.propertyOrNull("discord.clientId")?.getString()
    val guildId = cfg.propertyOrNull("discord.guildId")?.getString() // optional for testing

    if (!botToken.isNullOrBlank() && !clientId.isNullOrBlank()) {
        registerDiscordCommands(botToken, clientId, guildId)
    } else {
        log.info("[DiscordCmd] Not registered: botToken or clientId missing")
    }

    // Seed the database if SEED_DATABASE=true
    if (System.getenv("SEED_DATABASE")?.lowercase() == "true" && hasAll("storage.jdbcURL", "storage.user", "storage.password")) {
        log.info("SEED_DATABASE=true — running database seed")
        seedDatabase()
    }
}
