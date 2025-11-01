package org.bscm.migrations

import org.bscm.models.tables.*
import java.nio.file.Path

fun main(args: Array<String>) {
    val allTables = mapOf(
        "AccountTable" to AccountTable,
        "ChartStreamingLinkTable" to ChartStreamingLinkTable,
        "ChartTable" to ChartTable,
        "CollectionItemTable" to CollectionItemTable,
        "CollectionTable" to CollectionTable,
        "ContributorTable" to ContributorTable,
        "StreamingLinkTable" to StreamingLinkTable,
        "ThemeTable" to ThemeTable,
        "TourPassChartTable" to TourPassChartTable,
        "TourPassTable" to TourPassTable,
        "UserTable" to UserTable,
        "VersionTable" to VersionTable,
    )

    if (args.size < 5) {
        println("Usage: <jdbcUrl> <user> <password> <migration_name> <table1> [table2] ...")
        println("Available tables: ${allTables.keys.joinToString(", ")}")
        println("To include all tables, use 'all' as the table argument.")
        return
    }

    val jdbcUrl = "jdbc:" + args[0]
    val user = args[1]
    val password = args[2]
    val scriptName = args[3]
    val tableNames = args.drop(4)

    val includeAll = tableNames.any { it.equals("all", ignoreCase = true) || it.equals("--all", ignoreCase = true) }
    val selectedTables = if (includeAll) {
        allTables.values.toTypedArray()
    } else {
        tableNames.mapNotNull {
            allTables[it] ?: run {
                println("Table not found: $it")
                null
            }
        }.toTypedArray()
    }

    if (selectedTables.isEmpty()) {
        println("No valid table selected.")
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
        tables = selectedTables
    )
}