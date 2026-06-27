package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases(config: ApplicationConfig) {
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

    Database.connect(
        url = url,
        driver = driver,
        user = user,
        password = password
    )

    val flyway = Flyway.configure()
        .dataSource(url, user, password)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()

    flyway.migrate()

    log.info("Database migrations applied successfully")
}