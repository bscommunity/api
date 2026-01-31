package org.bscm.services

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.CollectionKind
import org.bscm.models.enums.ContentType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.ICollectionRepository
import java.util.*

class CollectionService(
    private val collectionRepository: ICollectionRepository,
    private val activityRepository: IActivityRepository
) {

    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean = false): Collection {
        require(name.isNotBlank()) { "Collection name cannot be blank" }
        require(name.length <= 30) { "Collection name must be 30 characters or less" }

        return collectionRepository.createCollection(userId, name, isPublic)
    }

    suspend fun getUserCollections(userId: UUID, limit: Int? = 20, offset: Int? = 0): List<Collection> {
        return collectionRepository.getUserCollections(userId, limit, offset)
    }

    /**
     * Get system collection items
     */
    suspend fun getSystemCollectionItems(
        userId: UUID,
        kind: CollectionKind,
        limit: Int? = null,
        offset: Int? = null
    ): List<CatalogItem> {
        val collection = collectionRepository.getOrCreateSystemCollection(userId, kind)
        return collectionRepository.getCollectionItems(collection.id, userId, null, limit, offset)
    }

    /**
     * Add item to system collection
     */
    suspend fun addToSystemCollection(userId: UUID, kind: CollectionKind, contentId: String): Boolean {
        val collection = collectionRepository.getOrCreateSystemCollection(userId, kind)
        val added = collectionRepository.addItemToCollection(collection.id, userId, contentId)

        if (added) {
            val type = when (kind) {
                CollectionKind.LIKES -> ActivityType.LIKED_CONTENT
                CollectionKind.BOOKMARKS -> ActivityType.BOOKMARKED_CONTENT
                CollectionKind.USER -> null
            }

            type?.let { activityRepository.logActivity(userId, it, contentId) }
        }

        return added
    }

    /**
     * Remove item from system collection
     */
    suspend fun removeFromSystemCollection(userId: UUID, kind: CollectionKind, contentId: String): Boolean {
        val collection = collectionRepository.getOrCreateSystemCollection(userId, kind)
        return collectionRepository.removeItemFromCollection(collection.id, userId, contentId)
    }

    suspend fun updateCollection(collectionId: UUID, userId: UUID, name: String? = null, isPublic: Boolean? = null): Boolean {
        // Validate that we're not updating a system collection
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) {
            return false // Cannot update system collections
        }

        name?.let {
            require(it.isNotBlank()) { "Collection name cannot be blank" }
            require(it.length <= 30) { "Collection name must be 30 characters or less" }
        }

        return collectionRepository.updateCollection(collectionId, userId, name, isPublic)
    }

    suspend fun deleteCollection(collectionId: UUID, userId: UUID): Boolean {
        // Validate that we're not deleting a system collection
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) {
            return false // Cannot delete system collections
        }

        return collectionRepository.deleteCollection(collectionId, userId)
    }

    suspend fun addItemToCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean {
        // Validate that it's a USER collection
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection?.kind != CollectionKind.USER) {
            return false // Can only add to USER collections via this method
        }

        return collectionRepository.addItemToCollection(collectionId, userId, contentId)
    }

    suspend fun removeItemFromCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean {
        return collectionRepository.removeItemFromCollection(collectionId, userId, contentId)
    }

    suspend fun getCollectionItems(collectionId: UUID, userId: UUID? = null, category: ContentType? = null, limit: Int? = null, offset: Int? = null): List<CatalogItem> {
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
}
