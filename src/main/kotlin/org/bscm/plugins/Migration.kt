package org.bscm.plugins

import org.flywaydb.core.Flyway

fun migrateDatabase(jdbcUrl: String, user: String, password: String) {
    val flyway = Flyway.configure()
        .dataSource(jdbcUrl, user, password)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true) // Initialize the migration history table if it doesn't exist
        .load()

    // Executa as migrações
    flyway.migrate()
}