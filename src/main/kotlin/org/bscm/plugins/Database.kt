package org.bscm.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContributorTable
import org.bscm.models.tables.UserTable
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
        // SchemaUtils.drop(UserTable, ChartTable, VersionTable, ContributorTable)
        // SchemaUtils.drop(ChartTable, VersionTable, ContributorTable)
        SchemaUtils.create(UserTable, ChartTable, VersionTable, ContributorTable)
    }
}