package org.bscm.services

import org.bscm.models.Collection
import org.bscm.models.CollectionItem
import org.bscm.models.enums.ContentType
import org.bscm.repository.UserCollectionRepository
import org.bscm.routes.BatchInteractionRequest
import org.bscm.routes.BatchInteractionResponse
import org.bscm.routes.BatchInteractionResult
import org.bscm.routes.InteractionType
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
     * Check if content is liked by user
     */
    suspend fun isContentLiked(userId: UUID, contentType: ContentType, contentId: ULong): Boolean {
        val likesCollection = getOrCreateLikesCollection(userId)
        return collectionRepository.isItemInCollection(likesCollection.id, contentType, contentId)
    }

    /**
     * Get user's liked content with optional filtering and pagination
     */
    suspend fun getUserLikedContent(
        userId: UUID,
        contentType: ContentType? = null,
        limit: Int? = null,
        offset: Int? = null
    ): List<CollectionItem> {
        val likesCollection = getOrCreateLikesCollection(userId)
        val allLikedItems = collectionRepository.getCollectionItems(likesCollection.id, userId)

        var filteredItems = allLikedItems

        // Filter by content type if provided
        contentType?.let { type ->
            filteredItems = filteredItems.filter { it.contentType == type }
        }

        // Apply pagination
        val startIndex = offset ?: 0
        val endIndex = if (limit != null) {
            minOf(startIndex + limit, filteredItems.size)
        } else {
            filteredItems.size
        }

        return if (startIndex < filteredItems.size) {
            filteredItems.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    /**
     * Get content interaction statistics (likes, bookmarks count)
     */
    suspend fun getContentInteractionStats(contentType: ContentType, contentId: ULong): Map<String, Any> {
        // Get likes count for this content
        val likesCount = collectionRepository.getContentStats(LIKES_COLLECTION_NAME, contentType, contentId)
            .getOrDefault("count", 0)

        // Get bookmarks count for this content (favorites)
        val bookmarksCount = collectionRepository.getContentStats(FAVORITES_COLLECTION_NAME, contentType, contentId)
            .getOrDefault("count", 0)

        return mapOf(
            "contentType" to contentType.name,
            "contentId" to contentId,
            "likesCount" to likesCount,
            "bookmarksCount" to bookmarksCount
        )
    }

    /**
     * Batch process multiple interactions in a single request
     */
    suspend fun batchProcessInteractions(userId: UUID, request: BatchInteractionRequest): BatchInteractionResponse {
        val results = mutableListOf<BatchInteractionResult>()
        val failedInteractions = mutableListOf<String>()
        var overallSuccess = true

        for ((index, interaction) in request.interactions.withIndex()) {
            try {
                val interactionId = "interaction_$index"
                val contentId = interaction.contentId.toULongOrNull()

                if (contentId == null) {
                    val errorMsg = "Invalid contentId: ${interaction.contentId}"
                    results.add(BatchInteractionResult(interactionId, false, errorMsg))
                    failedInteractions.add(interactionId)
                    overallSuccess = false
                    continue
                }

                val success = when (interaction.interactionType) {
                    InteractionType.LIKE -> {
                        likeContent(userId, interaction.contentType, contentId)
                    }

                    InteractionType.UNLIKE -> {
                        unlikeContent(userId, interaction.contentType, contentId)
                    }

                    InteractionType.BOOKMARK -> {
                        if (interaction.collectionId != null) {
                            // Add to specific collection
                            val collectionId = interaction.collectionId.toULongOrNull()
                                ?: throw IllegalArgumentException("Invalid collection ID")
                            addItemToCollection(collectionId, userId, interaction.contentType, contentId)
                        } else {
                            // Add to favorites
                            addToFavorites(userId, interaction.contentType, contentId)
                        }
                    }

                    InteractionType.UNBOOKMARK -> {
                        if (interaction.collectionId != null) {
                            // Remove from specific collection
                            val collectionId = interaction.collectionId.toULongOrNull()
                                ?: throw IllegalArgumentException("Invalid collection ID")
                            removeItemFromCollection(collectionId, userId, interaction.contentType, contentId)
                        } else {
                            // Remove from favorites
                            removeFromFavorites(userId, interaction.contentType, contentId)
                        }
                    }
                }

                results.add(BatchInteractionResult(interactionId, success, null))

                if (!success) {
                    failedInteractions.add(interactionId)
                    overallSuccess = false
                }

            } catch (e: Exception) {
                val interactionId = "interaction_$index"
                val errorMsg = e.message ?: "Unknown error occurred"
                results.add(BatchInteractionResult(interactionId, false, errorMsg))
                failedInteractions.add(interactionId)
                overallSuccess = false
            }
        }

        return BatchInteractionResponse(
            success = overallSuccess,
            results = results,
            failedInteractions = failedInteractions
        )
    }
}
