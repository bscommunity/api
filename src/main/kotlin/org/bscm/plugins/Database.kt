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
                org.bscm.models.tables.TrackStreamingRefTable,
                org.bscm.models.tables.ChartTable,
                org.bscm.models.tables.CollectionItemTable,
                org.bscm.models.tables.CollectionTable,
                org.bscm.models.tables.CatalogItemTable,
                org.bscm.models.tables.ContributorTable,
                org.bscm.models.tables.ThemeTable,
                org.bscm.models.tables.TourPassChartTable,
                org.bscm.models.tables.TourPassTable,
                org.bscm.models.tables.UserTable,
                org.bscm.models.tables.VersionTable,
                org.bscm.models.tables.BundleUrlCacheTable,
                org.bscm.models.tables.ChangelogTable,
            )

            // Ensure shared-PK tables have a PRIMARY KEY before adding FKs.
            // SchemaUtils.create() uses CREATE TABLE IF NOT EXISTS, so if a previous run left
            // broken tables (missing PK), we need to add it here.
            exec("""
                DO $$ BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_constraint pc ON pc.conrelid = c.oid WHERE c.relname = 'tour_passes' AND pc.contype = 'p') THEN
                        ALTER TABLE tour_passes ADD PRIMARY KEY (id);
                    END IF;
                END $$;
            """.trimIndent())
            exec("""
                DO $$ BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_constraint pc ON pc.conrelid = c.oid WHERE c.relname = 'charts' AND pc.contype = 'p') THEN
                        ALTER TABLE charts ADD PRIMARY KEY (id);
                    END IF;
                END $$;
            """.trimIndent())
            exec("""
                DO $$ BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_constraint pc ON pc.conrelid = c.oid WHERE c.relname = 'themes' AND pc.contype = 'p') THEN
                        ALTER TABLE themes ADD PRIMARY KEY (id);
                    END IF;
                END $$;
            """.trimIndent())

            // FK for TourPassChartTable — cannot use reference() in CompositeIdTable because
            // the referenced columns are themselves references (shared-PK pattern) and Exposed's
            // DDL sorting creates them before the target tables' PKs are fully established.
            // The PK DO blocks above ensure PKs exist before we add these FKs.
            exec("""
                DO $$ BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tpc_tour_pass') THEN
                        ALTER TABLE tour_pass_charts ADD CONSTRAINT fk_tpc_tour_pass FOREIGN KEY (tour_pass_id) REFERENCES tour_passes(id) ON DELETE CASCADE;
                    END IF;
                END $$;
            """.trimIndent())
            exec("""
                DO $$ BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tpc_chart') THEN
                        ALTER TABLE tour_pass_charts ADD CONSTRAINT fk_tpc_chart FOREIGN KEY (chart_id) REFERENCES charts(id) ON DELETE CASCADE;
                    END IF;
                END $$;
            """.trimIndent())

            exec("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_collections_user_kind_non_user
                ON collections (user_id, kind)
                WHERE kind != 'USER'
            """.trimIndent())
        }
        log.info("Database initialized")

        val flyway = Flyway.configure()
            .dataSource(url, user, password)
            .baselineOnMigrate(true)
            .baselineVersion("0")  // ← use 0, not 1
            .load()

        // Executa as migrações
        flyway.migrate()

        log.info("Database migrations applied successfully")
    } catch (e: Exception) {
        log.error("Failed to initialize database", e)
    }
}