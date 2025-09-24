package org.bscm.repository

import org.bscm.models.Collection
import org.bscm.models.CollectionItem
import org.bscm.models.enums.ContentType
import java.util.*

interface UserCollectionRepository {
    // Collection Management
    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean): Collection
    suspend fun getUserCollections(userId: UUID, limit: Int? = null, offset: Int? = null): List<Collection>
    suspend fun getCollection(collectionId: ULong, userId: UUID? = null): Collection?
    suspend fun updateCollection(collectionId: ULong, userId: UUID, name: String?, isPublic: Boolean?): Boolean
    suspend fun deleteCollection(collectionId: ULong, userId: UUID): Boolean

    // Collection Item Management
    suspend fun addItemToCollection(collectionId: ULong, userId: UUID, contentType: ContentType, contentId: ULong): Boolean
    suspend fun removeItemFromCollection(collectionId: ULong, userId: UUID, contentType: ContentType, contentId: ULong): Boolean
    suspend fun getCollectionItems(collectionId: ULong, userId: UUID? = null): List<CollectionItem>
    suspend fun isItemInCollection(collectionId: ULong, contentType: ContentType, contentId: ULong): Boolean
    suspend fun getUserCollectionsContaining(userId: UUID, contentType: ContentType, contentId: ULong): List<Collection>

    /**
     * Get content statistics from a specific system collection (e.g., "Likes")
     * Returns how many users have this content in the specified collection
     */
    suspend fun getContentStats(collectionName: String, contentType: ContentType, contentId: ULong): Map<String, Int>
}
