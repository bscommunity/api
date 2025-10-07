package org.bscm.services

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.dto.collection.CreateCollectionItemRequest
import org.bscm.models.enums.ContentType
import org.bscm.repository.CollectionRepository
import java.util.*

class CollectionService(
    private val collectionRepository: CollectionRepository
) {

    companion object {
        const val FAVORITES_COLLECTION_NAME = "Favorites"
        const val LIKES_COLLECTION_NAME = "Likes"

        // System collections that users cannot delete or rename
        val SYSTEM_COLLECTIONS = setOf(FAVORITES_COLLECTION_NAME, LIKES_COLLECTION_NAME)
    }

    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean = false): Collection {
        require(name.isNotBlank()) { "Collection name cannot be blank" }
        require(name.length <= 30) { "Collection name must be 30 characters or less" }

        return collectionRepository.createCollection(userId, name, isPublic)
    }

    suspend fun getUserCollections(userId: UUID, limit: Int? = 20, offset: Int? = 0): List<Collection> {
        return collectionRepository.getUserCollections(userId, limit, offset)
    }

    suspend fun getCollection(collectionId: UUID, userId: UUID? = null): Collection? {
        return collectionRepository.getCollection(collectionId, userId)
    }

    /**
     * Get or create a system collection for a user
     */
    private suspend fun getOrCreateSystemCollection(userId: UUID, collectionName: String): Collection {
        val userCollections = collectionRepository.getUserCollections(userId)
        val systemCollection = userCollections.find { it.name == collectionName }

        return systemCollection ?: run {
            // Create system collection if it doesn't exist (always private)
            collectionRepository.createCollection(userId, collectionName, isPublic = false)
        }
    }

    suspend fun updateCollection(
        collectionId: UUID,
        userId: UUID,
        name: String? = null,
        isPublic: Boolean? = null
    ): Boolean {
        // Get collection first to check if it's a system collection
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection != null && collection.name in SYSTEM_COLLECTIONS) {
            // Cannot rename system collections, but can change visibility
            return if (name != null) {
                false // Cannot rename
            } else {
                collectionRepository.updateCollection(collectionId, userId, null, isPublic)
            }
        }

        name?.let {
            require(it.isNotBlank()) { "Collection name cannot be blank" }
            require(it.length <= 30) { "Collection name must be 30 characters or less" }
        }

        return collectionRepository.updateCollection(collectionId, userId, name, isPublic)
    }

    suspend fun deleteCollection(collectionId: UUID, userId: UUID): Boolean {
        // Get collection first to check if it's a system collection
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection != null && collection.name in SYSTEM_COLLECTIONS) {
            // Cannot delete system collections
            return false
        }

        return collectionRepository.deleteCollection(collectionId, userId)
    }

    suspend fun addItemToCollection(
        collectionId: UUID,
        userId: UUID,
        contentId: String,
    ): Boolean {
        return collectionRepository.addItemToCollection(collectionId, userId, contentId)
    }

    suspend fun removeItemFromCollection(
        collectionId: UUID,
        userId: UUID,
        contentId: String,
    ): Boolean {
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

    suspend fun batchProcessInteractions(
        userId: UUID,
        interactions: List<CreateCollectionItemRequest>
    ): Int {
        return collectionRepository.batchProcessInteractions(userId, interactions)
    }
}
