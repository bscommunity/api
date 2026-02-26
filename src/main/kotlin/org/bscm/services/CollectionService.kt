package org.bscm.services

import io.ktor.util.logging.*
import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.dto.collection.BatchCollectionItemRequest
import org.bscm.models.dto.collection.BatchCollectionItemResponse
import org.bscm.models.enums.ActionType
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.CollectionKind
import org.bscm.models.enums.ContentType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.ICollectionRepository
import java.time.LocalDateTime
import java.util.*

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
        contentId: String,
        kind: CollectionKind,
        collectionId: UUID? = null
    ): Boolean {
        val (resolvedId, resolvedKind) = resolveCollection(userId, kind, collectionId)
            ?: return false

        val added = collectionRepository.addItemToCollection(resolvedId, resolvedKind, userId, contentId)

        if (added) {
            logActivityForKind(userId, resolvedKind, contentId)
        }

        return added
    }

    /**
     * Remove an item from any collection — system or user-created.
     */
    suspend fun removeItem(
        userId: UUID,
        contentId: String,
        kind: CollectionKind,
        collectionId: UUID? = null
    ): Boolean {
        val (resolvedId, _) = resolveCollection(userId, kind, collectionId)
            ?: return false

        return collectionRepository.removeItemFromCollection(resolvedId, userId, contentId)
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
            val contentIds = items.map { it.contentId }

            try {
                val cacheKey = kind to collectionId
                val resolvedId = collectionIdCache.getOrPut(cacheKey) {
                    resolveCollection(userId, kind, collectionId?.let { UUID.fromString(it) })?.first
                        ?: run { failCount += contentIds.size; return@getOrPut null!! }
                }

                val activityTimestamp = items.firstNotNullOfOrNull { it.enqueuedAt } ?: LocalDateTime.now()

                val (success, failed) = when (action) {
                    ActionType.ADD -> {
                        val result = collectionRepository.batchAddItemsToCollection(resolvedId, userId, contentIds)

                        if (kind in setOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS) && result.first > 0) {
                            activityTypeForKind(kind)?.let { type ->
                                val successfulIds = contentIds.filterNot { it in result.second }
                                if (successfulIds.isNotEmpty()) {
                                    activityRepository.batchLogActivity(userId, type, successfulIds, activityTimestamp)
                                }
                            }
                        }

                        result.first to result.second.size
                    }
                    ActionType.REMOVE -> {
                        val deleted = collectionRepository.batchRemoveItemsFromCollection(resolvedId, userId, contentIds)
                        deleted to (contentIds.size - deleted)
                    }
                }

                successCount += success
                failCount += failed
            } catch (e: Exception) {
                log.error("Batch operation failed for group $key", e)
                failCount += contentIds.size
            }
        }

        return BatchCollectionItemResponse(successful = successCount, failed = failCount)
    }

    // ── Read operations ─────────────────────────────────────────────────────

    suspend fun getCollectionItems(
        userId: UUID,
        kind: CollectionKind,
        collectionId: UUID? = null,
        category: ContentType? = null,
        limit: Int? = null,
        offset: Int? = null
    ): List<CatalogItem> {
        val (resolvedId, _) = resolveCollection(userId, kind, collectionId)
            ?: return emptyList()

        return collectionRepository.getCollectionItems(resolvedId, userId, category, limit, offset)
    }

    suspend fun getCollection(collectionId: UUID, userId: UUID): Collection? =
        collectionRepository.getCollection(collectionId, userId)

    suspend fun getCollectionBySlug(username: String, slug: String, viewerId: UUID?): Collection? =
        collectionRepository.getCollectionBySlug(username, slug, viewerId)

    suspend fun getUserCollections(userId: UUID, limit: Int? = 20, offset: Int? = 0, onlyPublic: Boolean): List<Collection> =
        collectionRepository.getUserCollections(userId, limit, offset, onlyPublic)

    // ── Mutations ───────────────────────────────────────────────────────────

    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean = false): Collection {
        require(name.isNotBlank()) { "Collection name cannot be blank" }
        require(name.length <= 30) { "Collection name must be 30 characters or less" }
        return collectionRepository.createCollection(userId, name, isPublic)
    }

    suspend fun updateCollection(collectionId: UUID, userId: UUID, name: String? = null, isPublic: Boolean? = null): Boolean {
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) throw IllegalArgumentException("Only user collections can be updated")

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

    private suspend fun logActivityForKind(userId: UUID, kind: CollectionKind, contentId: String) {
        activityTypeForKind(kind)?.let { activityRepository.logActivity(userId, it, contentId) }
    }

    private fun activityTypeForKind(kind: CollectionKind): ActivityType? = when (kind) {
        CollectionKind.LIKES -> ActivityType.LIKED_CHART
        CollectionKind.BOOKMARKS -> ActivityType.BOOKMARKED_CHART
        CollectionKind.USER -> null
    }
}