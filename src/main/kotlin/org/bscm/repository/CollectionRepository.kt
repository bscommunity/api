package org.bscm.repository

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.dto.collection.CreateCollectionItemRequest
import org.bscm.models.enums.ContentType
import java.util.*

interface CollectionRepository {
    // Collection Management
    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean): Collection
    suspend fun getCollection(collectionId: ULong, userId: UUID? = null): Collection?
    suspend fun getUserCollections(userId: UUID, limit: Int? = null, offset: Int? = null): List<Collection>
    suspend fun updateCollection(collectionId: ULong, userId: UUID, name: String?, isPublic: Boolean?): Boolean
    suspend fun deleteCollection(collectionId: ULong, userId: UUID): Boolean

    // Collection Item Management
    suspend fun getCollectionItems(collectionId: ULong, userId: UUID? = null, category: ContentType?, limit: Int? = null, offset: Int? = null): List<CatalogItem>
    suspend fun addItemToCollection(collectionId: ULong, userId: UUID, contentId: ULong): Boolean
    suspend fun removeItemFromCollection(collectionId: ULong, userId: UUID, contentId: ULong): Boolean
    suspend fun isItemInCollection(collectionId: ULong, contentId: ULong): Boolean
    suspend fun batchProcessInteractions(userId: UUID, interactions: List<CreateCollectionItemRequest>): Int
}
