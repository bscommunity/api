package org.bscm.models.interfaces

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.enums.CollectionKind
import org.bscm.models.enums.ContentType
import java.util.*

interface ICollectionRepository {
    // Collection Management
    suspend fun getOrCreateSystemCollectionId(userId: UUID, kind: CollectionKind): UUID

    suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean): Collection
    suspend fun getCollection(collectionId: UUID, userId: UUID? = null): Collection?
    suspend fun getCollectionBySlug(username: String, slug: String, userId: UUID?): Collection?
    suspend fun getUserCollections(userId: UUID, limit: Int? = null, offset: Int? = null, onlyPublic: Boolean = false): List<Collection>
    suspend fun updateCollection(collectionId: UUID, userId: UUID, name: String?, isPublic: Boolean?): Boolean
    suspend fun deleteCollection(collectionId: UUID, userId: UUID): Boolean

    // Collection Item Management
    suspend fun getCollectionItems(collectionId: UUID, userId: UUID? = null, category: ContentType?, limit: Int? = null, offset: Int? = null): List<CatalogItem>
    suspend fun addItemToCollection(collectionId: UUID, collectionKind: CollectionKind, userId: UUID, contentId: String): Boolean
    suspend fun removeItemFromCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean
    suspend fun isItemInCollection(collectionId: UUID, contentId: String): Boolean

    // Batch Operations
    suspend fun batchAddItemsToCollection(collectionId: UUID, userId: UUID, contentIds: List<String>): Pair<Int, List<String>>
    suspend fun batchRemoveItemsFromCollection(collectionId: UUID, userId: UUID, contentIds: List<String>): Int
}
