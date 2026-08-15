package org.bscm.utils

import io.ktor.util.logging.*
import kotlinx.datetime.LocalDateTime
import org.bscm.models.enums.CollectionKind
import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.select
import java.util.*

private val log = KtorSimpleLogger("UserStatsUtils")

/**
 * Utility object for fetching user interaction stats (likes and bookmarks) for catalog items.
 * Must be called within a transaction context.
 */
object UserStatsUtils {
    /**
     * Fetches user interaction stats for a batch of content items.
     * Returns a map of catalogId -> (isLiked, isBookmarked)
     */
    fun fetchUserStats(userId: UUID?, catalogIds: List<String>): Map<String, Pair<LocalDateTime?, LocalDateTime?>> {
        log.info("fetchUserStats called with userId=$userId, catalogIds=${catalogIds.joinToString()}")

        if (userId == null) {
            log.warn("userId is null, returning empty map")
            return emptyMap()
        }

        if (catalogIds.isEmpty()) {
            log.warn("catalogIds is empty, returning empty map")
            return emptyMap()
        }

        log.info("Querying database for user stats...")

        // Fetch all collection items for the user and the given catalog IDs in a single query
        val statsRows = CollectionItemTable
            .innerJoin(CollectionTable, { CollectionItemTable.collectionId }, { CollectionTable.id })
            .select(CollectionItemTable.catalogId, CollectionTable.kind, CollectionItemTable.addedAt)
            .where {
                (CollectionTable.userId eq userId) and (CollectionItemTable.catalogId inList catalogIds)
            }
            .toList() // Execute the query immediately

        log.info("Query returned ${statsRows.size} rows")

        /*if (statsRows.isNotEmpty()) {
            statsRows.forEach { row ->
                log.info("Row: catalogId=${row[CollectionItemTable.catalogId].value}, kind=${row[CollectionTable.kind]}")
            }
        }*/

        // Group results by catalogId and collect all collection kinds
        val catalogIdToTimes = statsRows.groupBy { it[CollectionItemTable.catalogId].value }
            .mapValues { (_, rows) ->
                val likedAt = rows.filter { it[CollectionTable.kind] == CollectionKind.LIKES }
                    .maxOfOrNull { it[CollectionItemTable.addedAt] }

                val bookmarkedAt = rows.filter {
                    val kind = it[CollectionTable.kind]
                    kind == CollectionKind.BOOKMARKS || kind == CollectionKind.USER
                }
                    .maxOfOrNull { it[CollectionItemTable.addedAt] }

                Pair (likedAt, bookmarkedAt)
            }

        val result = catalogIds.associateWith { catalogId ->
            val times = catalogIdToTimes[catalogId] ?: Pair(null, null)

            times
        }

        // log.info("Final result map size: ${result.size}")
        return result
    }
}