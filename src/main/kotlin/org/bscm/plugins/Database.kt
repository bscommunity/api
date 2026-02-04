package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
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
        transaction {
            addLogger(Slf4jSqlDebugLogger)
            SchemaUtils.create(
                org.bscm.models.tables.AccountTable,
                org.bscm.models.tables.ChartStreamingLinkTable,
                org.bscm.models.tables.ChartTable,
                org.bscm.models.tables.CollectionItemTable,
                org.bscm.models.tables.CollectionTable,
                org.bscm.models.tables.ContentTable,
                org.bscm.models.tables.ContributorTable,
                org.bscm.models.tables.StreamingLinkTable,
                org.bscm.models.tables.ThemeTable,
                org.bscm.models.tables.TourPassChartTable,
                org.bscm.models.tables.TourPassTable,
                org.bscm.models.tables.UserTable,
                org.bscm.models.tables.VersionTable,
            )
        }
        log.info("Database initialized")

        val flyway = Flyway.configure()
            .dataSource(url, user, password)
            .validateMigrationNaming(true)
            .baselineOnMigrate(true) // Used when migrating an existing database for the first time
            .load()

        // Executa as migrações
        flyway.migrate()

        log.info("Database migrations applied successfully")
    } catch (e: Exception) {
        log.error("Failed to initialize database", e)
    }
}