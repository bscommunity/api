package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases(config: ApplicationConfig) {
    // Allow running without database properties (e.g., unit tests focused on routing)
    val jdbcProp = config.propertyOrNull("storage.jdbcURL") ?: run {
        log.warn("Skipping database initialization: storage.jdbcURL not provided")
        return
    }
    val userProp = config.propertyOrNull("storage.user") ?: run {
        log.warn("Skipping database initialization: storage.user not provided")
        return
    }
    val passwordProp = config.propertyOrNull("storage.password") ?: run {
        log.warn("Skipping database initialization: storage.password not provided")
        return
    }

    val url = "jdbc:" + jdbcProp.getString()
    val driver = "org.postgresql.Driver"
    val user = userProp.getString()
    val password = passwordProp.getString()

    try {
        Database.connect(
            url = url,
            driver = driver,
            user = user,
            password = password
        )
        transaction { addLogger(StdOutSqlLogger) }
        log.info("Database initialized")
    } catch (e: Exception) {
        log.error("Failed to initialize database", e)
    }
}