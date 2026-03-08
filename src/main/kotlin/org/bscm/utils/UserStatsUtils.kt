package org.bscm.utils

import io.ktor.util.logging.*
import org.bscm.models.enums.CollectionKind
import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import java.time.LocalDateTime
import java.util.*

private val log = KtorSimpleLogger("UserStatsUtils")

/**
 * Utility object for fetching user interaction stats (likes and bookmarks) for catalog items.
 * Must be called within a transaction context.
 */
object UserStatsUtils {
    /**
     * Fetches user interaction stats for a batch of content items.
     * Returns a map of contentId -> (isLiked, isBookmarked)
     */
    fun fetchUserStats(userId: UUID?, contentIds: List<String>): Map<String, Pair<LocalDateTime?, LocalDateTime?>> {
        log.info("fetchUserStats called with userId=$userId, contentIds=${contentIds.joinToString()}")

        if (userId == null) {
            log.warn("userId is null, returning empty map")
            return emptyMap()
        }

        if (contentIds.isEmpty()) {
            log.warn("contentIds is empty, returning empty map")
            return emptyMap()
        }

        log.info("Querying database for user stats...")

        // Fetch all collection items for the user and the given content IDs in a single query
        val statsRows = CollectionItemTable
            .innerJoin(CollectionTable, { CollectionItemTable.collectionId }, { CollectionTable.id })
            .select(CollectionItemTable.contentId, CollectionTable.kind, CollectionItemTable.addedAt)
            .where {
                (CollectionTable.userId eq userId) and (CollectionItemTable.contentId inList contentIds)
            }
            .toList() // Execute the query immediately

        log.info("Query returned ${statsRows.size} rows")

        /*if (statsRows.isNotEmpty()) {
            statsRows.forEach { row ->
                log.info("Row: contentId=${row[CollectionItemTable.contentId].value}, kind=${row[CollectionTable.kind]}")
            }
        }*/

        // Group results by contentId and collect all collection kinds
        val contentIdToTimes = statsRows.groupBy { it[CollectionItemTable.contentId].value }
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

        // log.info("Grouped by contentId: ${contentIdToTimes.keys.joinToString()}")

        val result = contentIds.associateWith { contentId ->
            val times = contentIdToTimes[contentId] ?: Pair(null, null)

            // log.info("ContentId=$contentId: likedAt=${times.first}, bookmarkedAt=${times.second}")

            times
        }

        // log.info("Final result map size: ${result.size}")
        return result
    }
}