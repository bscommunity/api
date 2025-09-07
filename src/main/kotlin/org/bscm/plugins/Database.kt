package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases(config: ApplicationConfig) {
    val url = "jdbc:" + config.property("storage.jdbcURL").getString()
    val driver = "org.postgresql.Driver"
    val user = config.property("storage.user").getString()
    val password = config.property("storage.password").getString()

    // Execute migrations
    // Disabled for now as it causes issues in the server environment
    // migrateDatabase(url, user, password)

    // Connect to database
    Database.connect(
        url = url,
        driver = driver,
        user = user,
        password = password
    )

    // Log SQL to console
    transaction {
        addLogger(StdOutSqlLogger)
    }
}