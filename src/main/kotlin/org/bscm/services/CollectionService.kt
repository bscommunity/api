package org.bscm.services

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.enums.ContentType
import org.bscm.repository.UserCollectionRepository
import java.util.*

class CollectionService(
    private val collectionRepository: UserCollectionRepository
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

    suspend fun getCollection(collectionId: ULong, userId: UUID? = null): Collection? {
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

    /**
     * Get or create the default "Favorites" collection for a user
     */
    suspend fun getOrCreateFavoritesCollection(userId: UUID): Collection {
        return getOrCreateSystemCollection(userId, FAVORITES_COLLECTION_NAME)
    }

    /**
     * Get or create the default "Likes" collection for a user
     */
    suspend fun getOrCreateLikesCollection(userId: UUID): Collection {
        return getOrCreateSystemCollection(userId, LIKES_COLLECTION_NAME)
    }

    suspend fun updateCollection(
        collectionId: ULong,
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

    suspend fun deleteCollection(collectionId: ULong, userId: UUID): Boolean {
        // Get collection first to check if it's a system collection
        val collection = collectionRepository.getCollection(collectionId, userId)
        if (collection != null && collection.name in SYSTEM_COLLECTIONS) {
            // Cannot delete system collections
            return false
        }

        return collectionRepository.deleteCollection(collectionId, userId)
    }

    suspend fun addItemToCollection(
        collectionId: ULong,
        userId: UUID,
        contentId: ULong,
    ): Boolean {
        return collectionRepository.addItemToCollection(collectionId, userId, contentId)
    }

    suspend fun removeItemFromCollection(
        collectionId: ULong,
        userId: UUID,
        contentId: ULong,
    ): Boolean {
        return collectionRepository.removeItemFromCollection(collectionId, userId, contentId)
    }

    suspend fun getCollectionItems(
        collectionId: ULong,
        userId: UUID? = null,
        category: ContentType? = null,
        limit: Int? = null,
        offset: Int? = null
    ): List<CatalogItem> {
        return collectionRepository.getCollectionItems(collectionId, userId, category, limit, offset)
    }

    suspend fun batchProcessInteractions(
        userId: UUID,
        interactions: List<Pair<ULong, Boolean>>
    ): Int {
        return collectionRepository.batchProcessInteractions(userId, interactions)
    }

    /**
     * Add item to user's favorites (convenience method)
     */
    suspend fun addToFavorites(userId: UUID, contentId: ULong): Boolean {
        val favoritesCollection = getOrCreateFavoritesCollection(userId)
        return collectionRepository.addItemToCollection(favoritesCollection.id, userId, contentId)
    }

    /**
     * Remove item from user's favorites (convenience method)
     */
    suspend fun removeFromFavorites(userId: UUID, contentId: ULong): Boolean {
        val favoritesCollection = getOrCreateFavoritesCollection(userId)
        return collectionRepository.removeItemFromCollection(favoritesCollection.id, userId, contentId)
    }

    /**
     * Check if item is in user's favorites
     */
    suspend fun isInFavorites(userId: UUID, contentId: ULong): Boolean {
        val favoritesCollection = getOrCreateFavoritesCollection(userId)
        return collectionRepository.isItemInCollection(favoritesCollection.id, contentId)
    }

    /**
     * Get user's favorite items
     */
    suspend fun getUserFavorites(
        userId: UUID,
        category: ContentType? = null,
        limit: Int? = null,
        offset: Int? = null
    ): List<CatalogItem> {
        val favoritesCollection = getOrCreateFavoritesCollection(userId)
        return collectionRepository.getCollectionItems(favoritesCollection.id, userId, category, limit, offset)
    }

    /**
     * Add item to user's likes (convenience method)
     */
    suspend fun likeContent(userId: UUID, contentId: ULong): Boolean {
        val likesCollection = getOrCreateLikesCollection(userId)
        return collectionRepository.addItemToCollection(likesCollection.id, userId, contentId)
    }

    /**
     * Remove item from user's likes (convenience method)
     */
    suspend fun unlikeContent(userId: UUID, contentId: ULong): Boolean {
        val likesCollection = getOrCreateLikesCollection(userId)
        return collectionRepository.removeItemFromCollection(likesCollection.id, userId, contentId)
    }

    /**
     * Check if content is liked by user
     */
    suspend fun isContentLiked(userId: UUID, contentId: ULong): Boolean {
        val likesCollection = getOrCreateLikesCollection(userId)
        return collectionRepository.isItemInCollection(likesCollection.id, contentId)
    }

    /**
     * Get user's liked content with optional filtering and pagination
     */
    suspend fun getUserLikedContent(
        userId: UUID,
        contentType: ContentType? = null,
        limit: Int? = null,
        offset: Int? = null
    ): List<CatalogItem> {
        val likesCollection = getOrCreateLikesCollection(userId)
        return collectionRepository.getCollectionItems(likesCollection.id, userId, contentType, limit, offset)
    }
}
