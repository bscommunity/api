package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.bscm.models.tables.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases(config: ApplicationConfig) {
    val url = "jdbc:" + config.property("storage.jdbcURL").getString()
    val user = config.property("storage.user").getString()
    val password = config.property("storage.password").getString()

    // Execute migrations
    // migrateDatabase(url, user, password)

    // Connect to database
    Database.connect(
        url,
        user = user,
        password = password
    )

    // Initialize tables (if not already created)
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(
            UserTable,
            ChartTable,
            VersionTable,
            ContributorTable,
            StreamingLinkTable,
            ChartStreamingLinkTable
        )
    }
}