package org.bscm.migrations

import MigrationUtils
import io.github.classgraph.ClassGraph
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.size < 4) {
        println("Usage: <jdbcUrl> <user> <password> <migration_name>")
        return
    }

    val jdbcUrl = "jdbc:" + args[0]
    val user = args[1]
    val password = args[2]
    val scriptName = args[3]

    val tables = loadAllExposedTables().values.toTypedArray()

    if (tables.isEmpty()) {
        println("No Exposed tables found in org.bscm.models.tables")
        return
    }

    val scriptDirectory = Path.of("src/main/resources/db/migration")

    generateExposedMigrationScript(
        jdbcUrl = jdbcUrl,
        driver = "org.postgresql.Driver",
        user = user,
        password = password,
        scriptDirectory = scriptDirectory,
        scriptName = scriptName,
        tables = tables
    )
}

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
    vararg tables: Table
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

fun loadAllExposedTables(): Map<String, Table> {
    val tables = mutableMapOf<String, Table>()

    ClassGraph()
        .enableClassInfo()
        .acceptPackages("org.bscm.models.tables")
        .scan()
        .use { scanResult ->
            scanResult
                .getSubclasses(Table::class.java.name)
                .forEach { classInfo ->
                    val kClass = classInfo.loadClass().kotlin
                    val instance = kClass.objectInstance

                    if (instance is Table) {
                        tables[kClass.simpleName!!] = instance
                    }
                }
        }

    return tables
}
