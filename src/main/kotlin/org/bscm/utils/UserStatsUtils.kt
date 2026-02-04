package org.bscm.utils

import io.klogging.noCoLogger
import org.bscm.models.enums.CollectionKind
import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import java.util.*

private val log = noCoLogger(UserStatsUtils::class)

/**
 * Utility object for fetching user interaction stats (likes and bookmarks) for catalog items.
 * Must be called within a transaction context.
 */
object UserStatsUtils {
    /**
     * Fetches user interaction stats for a batch of content items.
     * Returns a map of contentId -> (isLiked, isBookmarked)
     */
    fun fetchUserStats(userId: UUID?, contentIds: List<String>): Map<String, Pair<Boolean, Boolean>> {
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
            .select(CollectionItemTable.contentId, CollectionTable.kind)
            .where {
                (CollectionTable.userId eq userId) and
                        (CollectionItemTable.contentId inList contentIds)
            }
            .toList() // Execute the query immediately

        log.info("Query returned ${statsRows.size} rows")

        if (statsRows.isNotEmpty()) {
            statsRows.forEach { row ->
                log.info("Row: contentId=${row[CollectionItemTable.contentId].value}, kind=${row[CollectionTable.kind]}")
            }
        }

        // Group results by contentId and collect all collection kinds
        val contentIdToKinds = statsRows
            .groupBy { it[CollectionItemTable.contentId].value }
            .mapValues { (_, rows) ->
                rows.map { row -> row[CollectionTable.kind] }.toSet()
            }

        log.info("Grouped by contentId: ${contentIdToKinds.keys.joinToString()}")

        // Map each contentId to (isLiked, isBookmarked)
        val result = contentIds.associateWith { contentId ->
            val kinds = contentIdToKinds[contentId] ?: emptySet()

            val isLiked = kinds.contains(CollectionKind.LIKES)
            // Item is bookmarked if in BOOKMARKS collection or any USER collection
            val isBookmarked = kinds.contains(CollectionKind.BOOKMARKS) || kinds.contains(CollectionKind.USER)

            log.info("ContentId=$contentId: kinds=$kinds, isLiked=$isLiked, isBookmarked=$isBookmarked")

            Pair(isLiked, isBookmarked)
        }

        log.info("Final result map size: ${result.size}")
        return result
    }
}