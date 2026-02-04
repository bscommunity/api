package org.bscm.migrations

import io.klogging.noCoLogger
import org.bscm.models.enums.ContentType
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContentTable
import org.bscm.models.tables.ContributorTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

private val log = noCoLogger("PopulateNotNullValuesCLI")

fun main(args: Array<String>) {
    if (args.size < 2) {
        log.info("Usage: <jdbcUrl> <user> <password>")
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
        // Step 1: Find charts without content_id or author_id
        val chartsWithoutContent = ChartTable
            .selectAll()
            .where { ChartTable.contentId.isNull() or ChartTable.authorId.isNull() }
            .toList()

        log.info("Found ${chartsWithoutContent.size} charts missing content or author.")

        chartsWithoutContent.forEach { chartRow ->
            val chartId = chartRow[ChartTable.id]

            // Step 2: Find the contributor for this chart (assuming one per chart)
            val contributor = ContributorTable
                .selectAll()
                .where { ContributorTable.chartId eq chartId }
                .singleOrNull()

            if (contributor == null) {
                log.warn("Chart $chartId has no contributor; skipping.")
                return@forEach
            }

            val userId = contributor[ContributorTable.userId]

            // Step 3: Create a new content entry
            val contentId = ContentTable.insertAndGetId {
                it[type] = ContentType.CHART
                it[createdAt] = LocalDateTime.now()
            }

            // Step 4: Update the chart row with new content and author
            ChartTable.update({ ChartTable.id eq chartId }) {
                it[ChartTable.contentId] = contentId
                it[ChartTable.authorId] = userId
                it[ChartTable.latestUpdatedAt] = LocalDateTime.now()
            }

            log.info("✅ Updated chart $chartId with content $contentId and author $userId")
        }
    }

    log.info("🎉 populateNotNullValues() finished.")
}