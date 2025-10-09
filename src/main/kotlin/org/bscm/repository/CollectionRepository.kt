package org.bscm.repository

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.enums.ActionOption
import org.bscm.models.enums.ContentType
import java.util.*

interface CollectionRepository {
    // Collection Management
    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean): Collection
    suspend fun getCollection(collectionId: UUID, userId: UUID? = null): Collection?
    suspend fun getUserCollections(userId: UUID, limit: Int? = null, offset: Int? = null): List<Collection>
    suspend fun updateCollection(collectionId: UUID, userId: UUID, name: String?, isPublic: Boolean?): Boolean
    suspend fun deleteCollection(collectionId: UUID, userId: UUID): Boolean

    // Collection Item Management
    suspend fun getCollectionItems(collectionId: UUID, userId: UUID? = null, category: ContentType?, limit: Int? = null, offset: Int? = null): List<CatalogItem>
    suspend fun addItemToCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean
    suspend fun removeItemFromCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean
    suspend fun isItemInCollection(collectionId: UUID, contentId: String): Boolean
    suspend fun batchProcessInteractions(userId: UUID, collectionId: UUID, itemsIds: List<String>, action: ActionOption): Int
}
