package org.bscm.services

import io.ktor.util.logging.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.dto.collection.BatchCollectionItemRequest
import org.bscm.models.dto.collection.BatchCollectionItemResponse
import org.bscm.models.enums.ActionType
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.CollectionKind
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.ICollectionRepository
import java.util.*
import kotlin.time.Clock

private val log = KtorSimpleLogger("CollectionService")

class CollectionService(
    private val collectionRepository: ICollectionRepository,
    private val activityRepository: IActivityRepository
) {

    // ── Write operations ────────────────────────────────────────────────────

    /**
     * Add an item to any collection — system (LIKES/BOOKMARKS) or user-created.
     *
     * For system collections, [collectionId] is null and [kind] drives the lookup.
     * For user collections, [collectionId] is required and [kind] is USER.
     *
     * Returns true if the item was inserted, false if it already existed or the
     * collection wasn't found.
     */
    suspend fun addItem(
        userId: UUID,
        catalogId: String,
        kind: CollectionKind,
        collectionId: UUID? = null
    ): Boolean {
        val (resolvedId, resolvedKind) = resolveCollection(userId, kind, collectionId)
            ?: return false

        val added = collectionRepository.addItemToCollection(resolvedId, resolvedKind, userId, catalogId)

        if (added) {
            logActivityForKind(userId, resolvedKind, catalogId)
        }

        return added
    }

    /**
     * Remove an item from any collection — system or user-created.
     */
    suspend fun removeItem(
        userId: UUID,
        catalogId: String,
        kind: CollectionKind,
        collectionId: UUID? = null
    ): Boolean {
        val (resolvedId, resolvedKind) = resolveCollection(userId, kind, collectionId)
            ?: return false

        val removed = collectionRepository.removeItemFromCollection(resolvedId, userId, catalogId)
        if (removed) {
            removeActivityForKind(userId, resolvedKind, catalogId)
        }

        return removed
    }

    // ── Batch ───────────────────────────────────────────────────────────────

    suspend fun processBatchCollectionItems(
        userId: UUID,
        requests: List<BatchCollectionItemRequest>
    ): BatchCollectionItemResponse {
        if (requests.isEmpty()) return BatchCollectionItemResponse(successful = 0, failed = 0)

        var successCount = 0
        var failCount = 0

        // Cache resolved collection IDs — avoids hitting DB for the same
        // (kind, collectionId) pair more than once across the whole batch.
        val collectionIdCache = mutableMapOf<Pair<CollectionKind, String?>, UUID>()

        val grouped = requests.groupBy { Triple(it.collectionId, it.collectionKind, it.action) }

        for ((key, items) in grouped) {
            val (collectionId, kind, action) = key
            val catalogIds = items.map { it.catalogId }

            try {
                val cacheKey = kind to collectionId
                val resolvedId = collectionIdCache.getOrPut(cacheKey) {
                    resolveCollection(userId, kind, collectionId?.let { UUID.fromString(it) })?.first
                        ?: run { failCount += catalogIds.size; null!! }
                }

                val activityTimestamp = items.firstNotNullOfOrNull { it.enqueuedAt } ?: Clock.System.now().toLocalDateTime(TimeZone.UTC)

                val (success, failed) = when (action) {
                    ActionType.ADD -> {
                        val result = collectionRepository.batchAddItemsToCollection(resolvedId, userId, catalogIds)

                        if (kind in setOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS) && result.first > 0) {
                            val successfulIds = catalogIds.filterNot { it in result.second }
                            if (successfulIds.isNotEmpty()) {
                                val typedIds = successfulIds.groupByNotNull { id ->
                                    collectionRepository.getContentType(id)?.let { contentType ->
                                        activityTypeForKind(kind, contentType)
                                    }
                                }

                                typedIds.forEach { (type, ids) ->
                                    // Keep only the latest active interaction per target.
                                    activityRepository.batchRemoveActivity(userId, type, ids)
                                    activityRepository.batchLogActivity(userId, type, ids, activityTimestamp)
                                }
                            }
                        }

                        result.first to result.second.size
                    }
                    ActionType.REMOVE -> {
                        val deleted = collectionRepository.batchRemoveItemsFromCollection(resolvedId, userId, catalogIds)

                        if (kind in setOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS) && deleted > 0) {
                            val typedIds = catalogIds.groupByNotNull { id ->
                                collectionRepository.getContentType(id)?.let { contentType ->
                                    activityTypeForKind(kind, contentType)
                                }
                            }

                            typedIds.forEach { (type, ids) ->
                                activityRepository.batchRemoveActivity(userId, type, ids)
                            }
                        }

                        deleted to (catalogIds.size - deleted)
                    }
                }

                successCount += success
                failCount += failed
            } catch (e: Exception) {
                log.error("Batch operation failed for group $key", e)
                failCount += catalogIds.size
            }
        }

        return BatchCollectionItemResponse(successful = successCount, failed = failCount)
    }

    // ── Read operations ─────────────────────────────────────────────────────

    suspend fun getCollectionItems(
        collectionId: UUID,
        userId: UUID? = null,
        categories: List<CatalogItemType>? = null,
        limit: Int? = null,
        offset: Int? = null
    ): Pair<List<CatalogItem>, Triple<Int, Int, Int>?> {
        val items = collectionRepository.getCollectionItems(collectionId, userId, categories, limit, offset)

        // Only fetch counts if we're on the first page (offset 0 or null)
        val counts = if (offset == null || offset == 0) {
            collectionRepository.getCollectionItemsCounts(collectionId)
        } else null

        return items to counts
    }

    suspend fun getSystemCollectionItems(
        userId: UUID,
        kind: CollectionKind,
        categories: List<CatalogItemType>? = null,
        limit: Int? = null,
        offset: Int? = null
    ): Pair<List<CatalogItem>, Triple<Int, Int, Int>?> {
        val items = collectionRepository.getCollectionItemsByKind(userId, kind, categories, limit, offset)

        // Only fetch counts if we're on the first page (offset 0 or null)
        val counts = if (offset == null || offset == 0) {
            collectionRepository.getCollectionItemCountsByKind(userId, kind)
        } else null

        return items to counts
    }

    suspend fun getCollection(collectionId: UUID, userId: UUID): Collection? =
        collectionRepository.getCollection(collectionId, userId)

    suspend fun getCollectionBySlug(username: String, slug: String, viewerId: UUID?): Collection? =
        collectionRepository.getCollectionBySlug(username, slug, viewerId)

    /**
     * Returns a page of collections together with the total number of collections
     * owned by the user (ignoring pagination), so callers avoid a second count query.
     */
    suspend fun getUserCollections(
        userId: UUID,
        limit: Int? = 20,
        offset: Int? = 0,
        onlyPublic: Boolean
    ): Pair<List<Collection>, Int> {
        val page  = collectionRepository.getUserCollections(userId, limit, offset, onlyPublic)
        val total = collectionRepository.getUserCollections(userId, null, null, onlyPublic).size
        return page to total
    }

    // ── Mutations ───────────────────────────────────────────────────────────

    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean = false): Collection {
        require(name.isNotBlank()) { "Collection name cannot be blank" }
        require(name.length <= 30) { "Collection name must be 30 characters or less" }
        return collectionRepository.createCollection(userId, name, isPublic)
    }

    suspend fun updateCollection(collectionId: UUID, userId: UUID, name: String? = null, isPublic: Boolean? = null): String? {
        // TODO: For now, for optimization, we add a SELECT clause in the UPDATE query
        //  to ensure the collection belongs to the user and is of kind USER.
        //  This means we don't need to do a separate getCollection call here,
        //  but it also means we can't validate the collection's existence or ownership before attempting the update.
        // val collection = collectionRepository.getCollection(collectionId, userId)
        // if (collection?.kind != CollectionKind.USER) throw IllegalArgumentException("Only user collections can be updated")

        name?.let {
            require(it.isNotBlank()) { "Collection name cannot be blank" }
            require(it.length <= 30) { "Collection name must be 30 characters or less" }
        }

        return collectionRepository.updateCollection(collectionId, userId, name, isPublic)
    }

    suspend fun deleteCollection(collectionId: UUID, userId: UUID): Boolean {
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) throw IllegalArgumentException("Only user collections can be deleted")
        return collectionRepository.deleteCollection(collectionId, userId)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Resolves a (collectionId, kind) pair into a concrete UUID.
     *
     * Think of this like a switchboard: given either a direct extension number
     * (explicit collectionId) or a department name (LIKES/BOOKMARKS), it always
     * hands back the actual room number (UUID) you need to knock on.
     *
     * Returns null if the inputs are inconsistent (e.g. USER kind without an ID).
     */
    private suspend fun resolveCollection(
        userId: UUID,
        kind: CollectionKind,
        collectionId: UUID?
    ): Pair<UUID, CollectionKind>? = when {
        kind == CollectionKind.USER && collectionId != null -> collectionId to kind
        kind in setOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS) ->
            collectionRepository.getOrCreateSystemCollectionId(userId, kind) to kind
        else -> null
    }

    private suspend fun logActivityForKind(userId: UUID, kind: CollectionKind, catalogId: String) {
        val contentType = collectionRepository.getContentType(catalogId) ?: return
        activityTypeForKind(kind, contentType)?.let {
            // Remove stale duplicates from prior add/remove cycles, then insert fresh activity.
            activityRepository.removeActivity(userId, it, catalogId)
            activityRepository.logActivity(userId, it, catalogId)
        }
    }

    private suspend fun removeActivityForKind(userId: UUID, kind: CollectionKind, catalogId: String) {
        val contentType = collectionRepository.getContentType(catalogId) ?: return
        activityTypeForKind(kind, contentType)?.let { activityRepository.removeActivity(userId, it, catalogId) }
    }

    private fun activityTypeForKind(kind: CollectionKind, catalogItemType: CatalogItemType): ActivityType? = when (kind) {
        CollectionKind.LIKES -> when (catalogItemType) {
            CatalogItemType.CHART -> ActivityType.LIKED_CHART
            CatalogItemType.TOUR_PASS -> ActivityType.LIKED_TOUR_PASS
            CatalogItemType.THEME -> ActivityType.LIKED_THEME
        }

        CollectionKind.BOOKMARKS -> when (catalogItemType) {
            CatalogItemType.CHART -> ActivityType.BOOKMARKED_CHART
            CatalogItemType.TOUR_PASS -> ActivityType.BOOKMARKED_TOUR_PASS
            CatalogItemType.THEME -> ActivityType.BOOKMARKED_THEME
        }

        CollectionKind.USER -> null
    }

    private inline fun <T, K> Iterable<T>.groupByNotNull(keySelector: (T) -> K?): Map<K, List<T>> =
        this.mapNotNull { element -> keySelector(element)?.let { it to element } }
            .groupBy({ it.first }, { it.second })
}