package org.bscm.services

import org.bscm.models.Collection
import org.bscm.models.CollectionItem
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

    suspend fun getUserCollections(userId: UUID): List<Collection> {
        return collectionRepository.getUserCollections(userId)
    }

    suspend fun getPublicCollections(limit: Int? = 20, offset: Int? = 0): List<Collection> {
        return collectionRepository.getPublicCollections(limit, offset)
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
        contentType: ContentType,
        contentId: ULong
    ): Boolean {
        return collectionRepository.addItemToCollection(collectionId, userId, contentType, contentId)
    }

    suspend fun removeItemFromCollection(
        collectionId: ULong,
        userId: UUID,
        contentType: ContentType,
        contentId: ULong
    ): Boolean {
        return collectionRepository.removeItemFromCollection(collectionId, userId, contentType, contentId)
    }

    suspend fun getCollectionItems(collectionId: ULong, userId: UUID? = null): List<CollectionItem> {
        return collectionRepository.getCollectionItems(collectionId, userId)
    }

    suspend fun getUserCollectionsContaining(
        userId: UUID,
        contentType: ContentType,
        contentId: ULong
    ): List<Collection> {
        return collectionRepository.getUserCollectionsContaining(userId, contentType, contentId)
    }

    /**
     * Add item to user's favorites (convenience method)
     */
    suspend fun addToFavorites(userId: UUID, contentType: ContentType, contentId: ULong): Boolean {
        val favoritesCollection = getOrCreateFavoritesCollection(userId)
        return collectionRepository.addItemToCollection(favoritesCollection.id, userId, contentType, contentId)
    }

    /**
     * Remove item from user's favorites (convenience method)
     */
    suspend fun removeFromFavorites(userId: UUID, contentType: ContentType, contentId: ULong): Boolean {
        val favoritesCollection = getOrCreateFavoritesCollection(userId)
        return collectionRepository.removeItemFromCollection(favoritesCollection.id, userId, contentType, contentId)
    }

    /**
     * Check if item is in user's favorites
     */
    suspend fun isInFavorites(userId: UUID, contentType: ContentType, contentId: ULong): Boolean {
        val favoritesCollection = getOrCreateFavoritesCollection(userId)
        return collectionRepository.isItemInCollection(favoritesCollection.id, contentType, contentId)
    }

    /**
     * Get user's favorite items
     */
    suspend fun getUserFavorites(userId: UUID): List<CollectionItem> {
        val favoritesCollection = getOrCreateFavoritesCollection(userId)
        return collectionRepository.getCollectionItems(favoritesCollection.id, userId)
    }

    /**
     * Add item to user's likes (convenience method)
     */
    suspend fun likeContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean {
        val likesCollection = getOrCreateLikesCollection(userId)
        return collectionRepository.addItemToCollection(likesCollection.id, userId, contentType, contentId)
    }

    /**
     * Remove item from user's likes (convenience method)
     */
    suspend fun unlikeContent(userId: UUID, contentType: ContentType, contentId: ULong): Boolean {
        val likesCollection = getOrCreateLikesCollection(userId)
        return collectionRepository.removeItemFromCollection(likesCollection.id, userId, contentType, contentId)
    }

    /**
     * Check if user liked this content
     */
    suspend fun isContentLiked(userId: UUID, contentType: ContentType, contentId: ULong): Boolean {
        val likesCollection = getOrCreateLikesCollection(userId)
        return collectionRepository.isItemInCollection(likesCollection.id, contentType, contentId)
    }

    /**
     * Get user's liked content
     */
    suspend fun getUserLikedContent(userId: UUID, contentType: ContentType? = null, limit: Int? = null, offset: Int? = null): List<CollectionItem> {
        val likesCollection = getOrCreateLikesCollection(userId)
        val allLikedItems = collectionRepository.getCollectionItems(likesCollection.id, userId)

        var filteredItems = if (contentType != null) {
            allLikedItems.filter { it.contentType == contentType }
        } else {
            allLikedItems
        }

        // Apply pagination
        if (offset != null) {
            filteredItems = filteredItems.drop(offset)
        }
        if (limit != null) {
            filteredItems = filteredItems.take(limit)
        }

        return filteredItems
    }

    /**
     * Get content interaction stats (likes count)
     */
    suspend fun getContentInteractionStats(contentType: ContentType, contentId: ULong): Map<String, Int> {
        // Count how many users have this item in their "Likes" collection
        // This requires a new method in the repository
        return collectionRepository.getContentStats(LIKES_COLLECTION_NAME, contentType, contentId)
    }
}
