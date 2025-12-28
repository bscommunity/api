package org.bscm.services

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.dto.collection.UpdateCollectionItemRequest
import org.bscm.models.enums.ContentType
import org.bscm.models.interfaces.ICollectionRepository
import java.util.*

class CollectionService(
    private val collectionRepository: ICollectionRepository
) {

    companion object {
        const val FAVORITES_COLLECTION_NAME = "favorites"
        const val LIKES_COLLECTION_NAME = "likes"

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

    /**
     * Resolves a collectionId string to the actual UUID for the user.
     * Supports system collections ("likes", "favorites") and UUIDs.
     */
    private suspend fun resolveCollectionIdForUser(userId: UUID, collectionId: String): UUID {
        return when (collectionId.lowercase()) {
            "likes" -> getOrCreateSystemCollection(userId, LIKES_COLLECTION_NAME).id
            "favorites" -> getOrCreateSystemCollection(userId, FAVORITES_COLLECTION_NAME).id
            else -> UUID.fromString(collectionId)
        }
    }

    suspend fun getCollection(collectionId: String, userId: UUID? = null): Collection? {
        val resolvedId = userId?.let { resolveCollectionIdForUser(it, collectionId) } ?: UUID.fromString(collectionId)
        return collectionRepository.getCollection(resolvedId, userId)
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

    suspend fun updateCollection(collectionId: String, userId: UUID, name: String? = null, isPublic: Boolean? = null): Boolean {
        val resolvedId = resolveCollectionIdForUser(userId, collectionId)

        // Get collection first to check if it's a system collection
        val collection = collectionRepository.getCollection(resolvedId, userId)
        if (collection != null && collection.name in SYSTEM_COLLECTIONS) {
            // Cannot rename system collections, but can change visibility
            return if (name != null) {
                false // Cannot rename
            } else {
                collectionRepository.updateCollection(resolvedId, userId, null, isPublic)
            }
        }

        name?.let {
            require(it.isNotBlank()) { "Collection name cannot be blank" }
            require(it.length <= 30) { "Collection name must be 30 characters or less" }
        }

        return collectionRepository.updateCollection(resolvedId, userId, name, isPublic)
    }

    suspend fun deleteCollection(collectionId: String, userId: UUID): Boolean {
        val resolvedId = resolveCollectionIdForUser(userId, collectionId)

        // Get collection first to check if it's a system collection
        val collection = collectionRepository.getCollection(resolvedId, userId)
        if (collection != null && collection.name in SYSTEM_COLLECTIONS) {
            // Cannot delete system collections
            return false
        }

        return collectionRepository.deleteCollection(resolvedId, userId)
    }

    suspend fun addItemToCollection(collectionId: String, userId: UUID, contentId: String): Boolean {
        val resolvedId = resolveCollectionIdForUser(userId, collectionId)
        return collectionRepository.addItemToCollection(resolvedId, userId, contentId)
    }

    suspend fun removeItemFromCollection(collectionId: String, userId: UUID, contentId: String): Boolean {
        val resolvedId = resolveCollectionIdForUser(userId, collectionId)
        return collectionRepository.removeItemFromCollection(resolvedId, userId, contentId)
    }

    suspend fun getCollectionItems(collectionId: String, userId: UUID? = null, category: ContentType? = null, limit: Int? = null, offset: Int? = null): List<CatalogItem> {
        val resolvedId = userId?.let { resolveCollectionIdForUser(it, collectionId) } ?: UUID.fromString(collectionId)
        return collectionRepository.getCollectionItems(resolvedId, userId, category, limit, offset)
    }

    suspend fun batchProcessInteractions(userId: UUID, request: List<UpdateCollectionItemRequest>): Int {
        return collectionRepository.batchProcessInteractions(userId, request)
    }
}
