package org.bscm.services

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

class CollectionService(
    private val collectionRepository: ICollectionRepository,
    private val activityRepository: IActivityRepository
) {
    /**
     * Process a single collection item operation (add or remove).
     * Handles both system collections (by kind) and user collections (by ID).
     *
     * Returns true if the operation succeeded, false otherwise.
     * Failures are silently ignored (not returned to client per requirements).
     */
    suspend fun processCollectionItem(
        userId: UUID,
        contentId: String,
        collectionId: String?,
        collectionKind: CollectionKind,
        action: ActionType
    ): Boolean {
        return try {
            when {
                collectionId != null && collectionKind == CollectionKind.USER -> {
                    val uuidCollectionId = UUID.fromString(collectionId)
                    when (action) {
                        ActionType.ADD -> addItemToCollection(uuidCollectionId, userId, contentId)
                        ActionType.REMOVE -> removeItemFromCollection(uuidCollectionId, userId, contentId)
                    }
                }
                collectionKind in setOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS) -> {
                    when (action) {
                        ActionType.ADD -> addToSystemCollection(userId, collectionKind, contentId)
                        ActionType.REMOVE -> removeFromSystemCollection(userId, collectionKind, contentId)
                    }
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Process batch collection items efficiently by grouping operations.
     *
     * Groups items by (collectionId, collectionKind, action) to minimise DB operations,
     * then resolves each system-collection UUID at most once per kind via an in-memory
     * cache — like fetching a store directory once instead of asking for directions
     * before every aisle visit.
     *
     * Activity logging is done in a single batch call per (type, action) group rather
     * than one INSERT per item.
     *
     * Returns a summary of successful and failed operations.
     */
    suspend fun processBatchCollectionItems(
        userId: UUID,
        requests: List<BatchCollectionItemRequest>
    ): BatchCollectionItemResponse {
        if (requests.isEmpty()) {
            return BatchCollectionItemResponse(successful = 0, failed = 0)
        }

        var successCount = 0
        var failCount = 0

        // Cache resolved system-collection UUIDs so we call getOrCreateSystemCollection
        // at most once per CollectionKind (LIKES / BOOKMARKS), regardless of how many
        // request groups reference the same kind.
        val systemCollectionCache = mutableMapOf<CollectionKind, UUID>()

        // Group by (collectionId, collectionKind, action) to batch DB operations.
        val grouped = requests.groupBy { Triple(it.collectionId, it.collectionKind, it.action) }

        for ((key, items) in grouped) {
            val (collectionId, collectionKind, action) = key
            val contentIds = items.map { it.contentId }

            try {
                // Resolve the actual collection UUID — use the cache for system collections.
                val resolvedCollectionId: UUID = when {
                    collectionKind == CollectionKind.USER && collectionId != null -> {
                        UUID.fromString(collectionId)
                    }
                    collectionKind in setOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS) -> {
                        systemCollectionCache.getOrPut(collectionKind) {
                            collectionRepository.getOrCreateSystemCollection(userId, collectionKind).id
                        }
                    }
                    else -> {
                        failCount += contentIds.size
                        continue
                    }
                }

                // Use the request's enqueuedAt if present, otherwise fall back to now.
                // For a group, all items share the same Triple key so we pick the first
                // non-null enqueuedAt as a representative timestamp — close enough for
                // activity logging; if strict per-item timestamps are needed, switch to
                // per-item logging instead.
                val activityTimestamp = items.firstNotNullOfOrNull { it.enqueuedAt } ?: LocalDateTime.now()

                // Execute batch operation and collect success/failure counts.
                val (success, failed) = when (action) {
                    ActionType.ADD -> {
                        val result = collectionRepository.batchAddItemsToCollection(
                            resolvedCollectionId, userId, contentIds
                        )

                        // Batch-log activities for system collections in one INSERT.
                        if (collectionKind in setOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS) && result.first > 0) {
                            val activityType = when (collectionKind) {
                                CollectionKind.LIKES -> ActivityType.LIKED_CHART
                                CollectionKind.BOOKMARKS -> ActivityType.BOOKMARKED_CHART
                                else -> null
                            }

                            activityType?.let { type ->
                                val successfulIds = contentIds.filterNot { it in result.second }
                                if (successfulIds.isNotEmpty()) {
                                    activityRepository.batchLogActivity(
                                        userId, type, successfulIds, activityTimestamp
                                    )
                                }
                            }
                        }

                        result
                    }
                    ActionType.REMOVE -> {
                        collectionRepository.batchRemoveItemsFromCollection(
                            resolvedCollectionId, userId, contentIds
                        )
                    }
                }

                successCount += success
                failCount += failed.size
            } catch (e: Exception) {
                e.printStackTrace()
                failCount += contentIds.size
            }
        }

        return BatchCollectionItemResponse(successful = successCount, failed = failCount)
    }

    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean = false): Collection {
        require(name.isNotBlank()) { "Collection name cannot be blank" }
        require(name.length <= 30) { "Collection name must be 30 characters or less" }

        return collectionRepository.createCollection(userId, name, isPublic)
    }

    suspend fun getUserCollections(userId: UUID, limit: Int? = 20, offset: Int? = 0, onlyPublic: Boolean): List<Collection> {
        return collectionRepository.getUserCollections(userId, limit, offset, onlyPublic)
    }

    suspend fun getSystemCollectionItems(
        userId: UUID,
        kind: CollectionKind,
        limit: Int? = null,
        offset: Int? = null
    ): List<CatalogItem> {
        val collection = collectionRepository.getOrCreateSystemCollection(userId, kind)
        return collectionRepository.getCollectionItems(collection.id, userId, null, limit, offset)
    }

    suspend fun addToSystemCollection(userId: UUID, kind: CollectionKind, contentId: String): Boolean {
        val collection = collectionRepository.getOrCreateSystemCollection(userId, kind)
        val added = collectionRepository.addItemToCollection(collection.id, userId, contentId)

        if (added) {
            val type = when (kind) {
                CollectionKind.LIKES -> ActivityType.LIKED_CHART
                CollectionKind.BOOKMARKS -> ActivityType.BOOKMARKED_CHART
                CollectionKind.USER -> null
            }
            type?.let { activityRepository.logActivity(userId, it, contentId) }
        }

        return added
    }

    suspend fun removeFromSystemCollection(userId: UUID, kind: CollectionKind, contentId: String): Boolean {
        val collection = collectionRepository.getOrCreateSystemCollection(userId, kind)
        return collectionRepository.removeItemFromCollection(collection.id, userId, contentId)
    }

    suspend fun updateCollection(collectionId: UUID, userId: UUID, name: String? = null, isPublic: Boolean? = null): Boolean {
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) return false

        name?.let {
            require(it.isNotBlank()) { "Collection name cannot be blank" }
            require(it.length <= 30) { "Collection name must be 30 characters or less" }
        }

        return collectionRepository.updateCollection(collectionId, userId, name, isPublic)
    }

    suspend fun deleteCollection(collectionId: UUID, userId: UUID): Boolean {
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) return false

        return collectionRepository.deleteCollection(collectionId, userId)
    }

    suspend fun addItemToCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean {
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) return false

        return collectionRepository.addItemToCollection(collectionId, userId, contentId)
    }

    suspend fun removeItemFromCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean {
        return collectionRepository.removeItemFromCollection(collectionId, userId, contentId)
    }

    suspend fun getCollectionItems(
        collectionId: UUID,
        userId: UUID? = null,
        category: ContentType? = null,
        limit: Int? = null,
        offset: Int? = null
    ): List<CatalogItem> {
        return collectionRepository.getCollectionItems(collectionId, userId, category, limit, offset)
    }

    suspend fun getSystemCollectionItemsForProfile(
        userId: UUID,
        kind: CollectionKind,
        viewerId: UUID?,
        allowPublic: Boolean,
        limit: Int
    ): List<CatalogItem> {
        require(kind != CollectionKind.USER) { "Cannot use USER kind as system collection" }

        val collection = collectionRepository.getOrCreateSystemCollection(userId, kind)

        val accessUserId = when {
            viewerId == userId -> viewerId
            allowPublic -> userId
            else -> null
        }

        return collectionRepository.getCollectionItems(
            collectionId = collection.id,
            userId = accessUserId,
            category = null,
            limit = limit,
            offset = 0
        )
    }

    suspend fun batchAddItemsToCollection(
        collectionId: UUID,
        userId: UUID,
        contentIds: List<String>
    ): Pair<Int, List<String>> {
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) return 0 to contentIds

        return collectionRepository.batchAddItemsToCollection(collectionId, userId, contentIds)
    }

    suspend fun batchRemoveItemsFromCollection(
        collectionId: UUID,
        userId: UUID,
        contentIds: List<String>
    ): Pair<Int, List<String>> {
        return collectionRepository.batchRemoveItemsFromCollection(collectionId, userId, contentIds)
    }
}