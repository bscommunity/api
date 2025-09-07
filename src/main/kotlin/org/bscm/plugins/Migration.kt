package org.bscm.plugins

import MigrationUtils
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path

/**
 * Generates an SQL script from your Exposed schema and saves it in scriptDirectory/scriptName.
 * Then you commit this file to resources/db/migration and Flyway applies it.
 */
@OptIn(ExperimentalDatabaseMigrationApi::class)
fun generateExposedMigrationScript(
    jdbcUrl: String,
    driver: String,
    user: String,
    password: String,
    scriptDirectory: Path,
    scriptName: String,
    vararg tables: org.jetbrains.exposed.sql.Table
) {
    // Connects to the DB so MigrationUtils can read the current schema
    Database.connect(url = jdbcUrl, driver = driver, user = user, password = password)

    // MigrationUtils already writes the file if we pass scriptDirectory/scriptName.
    // We call it inside a transaction for safety (some metadata ops depend on the connection)
    transaction {
        MigrationUtils.generateMigrationScript(
            *tables,
            scriptDirectory = scriptDirectory.toString(),
            scriptName = scriptName
        )
    }

    println("Migration generated: ${scriptDirectory.resolve(scriptName)}")
}


fun migrateDatabase(jdbcUrl: String, user: String, password: String) {
    /*// Generate the migration script
    val scriptDirectory = Path.of("src/main/resources/db/migration")
    val scriptName = "V1__datetime_conversion.sql"

    generateExposedMigrationScript(
        jdbcUrl = jdbcUrl,
        driver = "org.postgresql.Driver",
        user = user,
        password = password,
        scriptDirectory = scriptDirectory,
        scriptName = scriptName,
        tables = arrayOf(
            org.bscm.models.tables.AccountTable,
            org.bscm.models.tables.ContributorTable,
            org.bscm.models.tables.UserTable,
            org.bscm.models.tables.VersionTable,
        )
    )*/

    val flyway = Flyway.configure()
        .dataSource(jdbcUrl, user, password)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true) // Initialize the migration history table if it doesn't exist
        .validateMigrationNaming(true)
        .load()

    // Executa as migrações
    flyway.migrate()
}