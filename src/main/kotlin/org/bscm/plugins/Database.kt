package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.bscm.models.tables.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabases(config: ApplicationConfig) {
    val url = "jdbc:" + config.property("storage.jdbcURL").getString()
    val user = config.property("storage.user").getString()
    val password = config.property("storage.password").getString()

    Database.connect(
        url,
        user = user,
        password = password
    )

    // Initialize tables
    transaction {
        SchemaUtils.create(UserTable)
    }
}