package org.bscm.migrations

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: <jdbcUrl> <user> <password>")
        return
    }

    val jdbcUrl = "jdbc:" + args[0]
    val user = args[1]
    val password = args[2]

    Database.connect(
        url = System.getenv("JDBC_URL") ?: jdbcUrl,
        driver = "org.postgresql.Driver",
        user = System.getenv("DB_USER") ?: user,
        password = System.getenv("DB_PASSWORD") ?: password
    )

    transaction {
        // Step 1:
    }

    println("🎉 updateCharts() completed.")
}