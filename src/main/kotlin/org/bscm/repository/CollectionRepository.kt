package org.bscm.repository

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.dao.CollectionEntity
import org.bscm.models.dao.CollectionItemEntity
import org.bscm.models.dao.ContentEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dto.collection.UpdateCollectionItemRequest
import org.bscm.models.enums.ActionOption
import org.bscm.models.enums.ContentType
import org.bscm.models.repository.IChartRepository
import org.bscm.models.repository.ICollectionRepository
import org.bscm.models.repository.IThemeRepository
import org.bscm.models.repository.ITourPassRepository
import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.bscm.models.tables.ContentTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class CollectionRepository(
    private val chartRepository: IChartRepository,
    private val themeRepository: IThemeRepository,
    private val tourPassRepository: ITourPassRepository
) : ICollectionRepository {
    override suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean): Collection =
        newSuspendedTransaction {
            val now = LocalDateTime.now()
            val entity = CollectionEntity.new {
                user = UserEntity[userId]
                this.name = name
                this.isPublic = isPublic
                createdAt = now
                updatedAt = now
            }

            Collection(
                id = entity.id.value,
                userId = userId,
                name = entity.name,
                isPublic = entity.isPublic,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                itemCount = 0
            )
        }

    override suspend fun getUserCollections(
        userId: UUID,
        limit: Int?,
        offset: Int?
    ): List<Collection> = newSuspendedTransaction {
        val query = CollectionEntity.find { CollectionTable.userId eq userId }
            .orderBy(CollectionTable.updatedAt to SortOrder.DESC)

        val collections = if (limit != null) {
            query.limit(limit).offset(offset?.toLong() ?: 0)
        } else {
            query
        }

        collections.map { entity ->
            val itemCount = CollectionItemTable.selectAll()
                .where { CollectionItemTable.collectionId eq entity.id }
                .count().toInt()

            Collection(
                id = entity.id.value,
                userId = entity.user.id.value,
                name = entity.name,
                isPublic = entity.isPublic,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                itemCount = itemCount
            )
        }
    }

    override suspend fun getCollection(collectionId: UUID, userId: UUID?): Collection? = newSuspendedTransaction {
        val filter = if (userId != null) {
            (CollectionTable.id eq collectionId) and
                    ((CollectionTable.userId eq userId) or (CollectionTable.isPublic eq true))
        } else {
            (CollectionTable.id eq collectionId) and (CollectionTable.isPublic eq true)
        }

        CollectionTable.selectAll().where { filter }.firstOrNull()?.let { row ->
            val itemCount = CollectionItemTable.selectAll()
                .where { CollectionItemTable.collectionId eq collectionId }
                .count().toInt()

            Collection(
                id = row[CollectionTable.id].value,
                userId = row[CollectionTable.userId].value,
                name = row[CollectionTable.name],
                isPublic = row[CollectionTable.isPublic],
                createdAt = row[CollectionTable.createdAt],
                updatedAt = row[CollectionTable.updatedAt],
                itemCount = itemCount
            )
        }
    }

    override suspend fun updateCollection(collectionId: UUID, userId: UUID, name: String?, isPublic: Boolean?): Boolean = newSuspendedTransaction {
        val entity = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction false

        name?.let { entity.name = it }
        isPublic?.let { entity.isPublic = it }
        entity.updatedAt = LocalDateTime.now()
        true
    }

    override suspend fun deleteCollection(collectionId: UUID, userId: UUID): Boolean = newSuspendedTransaction {
        val entity = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction false

        entity.delete()
        true
    }

    override suspend fun addItemToCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean = newSuspendedTransaction {
        // Verify if the collection exists and belongs to the user
        val collection = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction false

        // Verify if the item already exists in the collection
        val existingFilter = (CollectionItemTable.collectionId eq collectionId) and
                Op.TRUE

        val existing = CollectionItemTable.selectAll().where { existingFilter }.firstOrNull()
        if (existing != null) return@newSuspendedTransaction false

        // Add item to collection
        CollectionItemEntity.new {
            this.collection = collection
            this.content = ContentEntity[contentId]
            this.addedAt = LocalDateTime.now()
        }

        // Update collection's updatedAt
        collection.updatedAt = LocalDateTime.now()
        true
    }

    override suspend fun removeItemFromCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean = newSuspendedTransaction {
        // Verify if the collection belongs to the user
        val collection = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction false

        val filter = (CollectionItemTable.collectionId eq collectionId) and
                Op.TRUE

        val item = CollectionItemEntity.find { filter }.firstOrNull()
            ?: return@newSuspendedTransaction false

        item.delete()
        collection.updatedAt = LocalDateTime.now()
        true
    }

    override suspend fun getCollectionItems(collectionId: UUID, userId: UUID?, category: ContentType?, limit: Int?, offset: Int?): List<CatalogItem> = newSuspendedTransaction {
        // Verify access to the collection
        val hasAccess = if (userId != null) {
            CollectionTable.selectAll().where {
                (CollectionTable.id eq collectionId) and
                        ((CollectionTable.userId eq userId) or (CollectionTable.isPublic eq true))
            }.count() > 0
        } else {
            CollectionTable.selectAll().where {
                (CollectionTable.id eq collectionId) and (CollectionTable.isPublic eq true)
            }.count() > 0
        }

        if (!hasAccess) return@newSuspendedTransaction emptyList()

        // First, get the collection items with proper filtering and pagination
        val categoryFilter = when (category) {
            null -> Op.TRUE
            else -> CollectionItemTable.contentId inSubQuery ContentTable
                .select(ContentTable.id)
                .where { ContentTable.type eq category }
        }

        val itemsQuery = CollectionItemTable
            .innerJoin(ContentTable, { CollectionItemTable.contentId }, { ContentTable.id })
            .selectAll()
            .where { (CollectionItemTable.collectionId eq collectionId) and categoryFilter }
            .orderBy(CollectionItemTable.addedAt to SortOrder.DESC)

        val items = if (limit != null) {
            itemsQuery.limit(limit).offset(offset?.toLong() ?: 0).toList()
        } else {
            itemsQuery.toList()
        }

        if (items.isEmpty()) return@newSuspendedTransaction emptyList()

        // Group items by content type for efficient batch fetching
        val itemsByType = items.groupBy { it[ContentTable.type] }
        val catalogItems = mutableListOf<CatalogItem>()

        // Fetch Charts with all related data
        itemsByType[ContentType.CHART]?.let { chartItems ->
            val contentIds = chartItems.map { it[CollectionItemTable.contentId].value }
            val chartResults = chartRepository.getCharts(
                userId = userId,
                contentIds = contentIds,
            )
            catalogItems.addAll(chartResults)
        }

        // Fetch Themes
        itemsByType[ContentType.THEME]?.let { themeItems ->
            val contentIds = themeItems.map { it[CollectionItemTable.contentId].value }
            val themeResults = themeRepository.getThemes(
                contentIds = contentIds,
                search = null,
                limit = null,
                offset = null
            )
            catalogItems.addAll(themeResults)
        }

        // Fetch TourPasses with charts
        itemsByType[ContentType.TOUR_PASS]?.let { tourPassItems ->
            val contentIds = tourPassItems.map { it[CollectionItemTable.contentId].value }
            val tourPassResults = tourPassRepository.getTourPasses(
                userId = null,
                contentIds = contentIds,
                search = null,
                limit = null,
                offset = null
            )
            catalogItems.addAll(tourPassResults)
        }

        // Restore original order from collection (by addedAt)
        val orderMap = items.mapIndexed { index, item -> 
            item[CollectionItemTable.contentId].value to index
        }.toMap()

        catalogItems.sortedBy { item ->
            val contentId = item.id
            contentId.let { orderMap[it] } ?: Int.MAX_VALUE
        }
    }

    override suspend fun isItemInCollection(collectionId: UUID, contentId: String): Boolean = newSuspendedTransaction {
        val filter = (CollectionItemTable.collectionId eq collectionId) and
                (CollectionItemTable.contentId eq contentId)

        CollectionItemTable.selectAll().where { filter }.count() > 0
    }

    override suspend fun batchProcessInteractions(userId: UUID, request: List<UpdateCollectionItemRequest>): Int = newSuspendedTransaction {
        // Group requests by collectionId and action to minimize DB queries
        val grouped = request.groupBy { Pair(it.collectionId, it.action) }
        var processedCount = 0
        for ((key, group) in grouped) {
            val (collectionIdStr, action) = key
            val contentIds = group.map { it.contentId }
            val isSystemCollection = collectionIdStr == "likes" || collectionIdStr == "favorites"
            val collectionId: UUID = if (isSystemCollection) {
                // Find or create the special collection for the user
                val existing = CollectionEntity.find {
                    (CollectionTable.userId eq userId) and (CollectionTable.name eq collectionIdStr)
                }.firstOrNull()
                existing?.id?.value ?: CollectionEntity.new {
                    user = UserEntity[userId]
                    name = collectionIdStr
                    isPublic = false
                    createdAt = LocalDateTime.now()
                    updatedAt = LocalDateTime.now()
                }.id.value
            } else {
                UUID.fromString(collectionIdStr)
            }
            when (action) {
                ActionOption.ADD -> {
                    // Find existing items to avoid duplicates
                    val existingIds = CollectionItemEntity.find {
                        (CollectionItemTable.collectionId eq collectionId) and
                        (CollectionItemTable.contentId inList contentIds)
                    }.map { it.content.id.value }.toSet()
                    val toAdd = contentIds.filterNot { it in existingIds }
                    if (toAdd.isNotEmpty()) {
                        val now = LocalDateTime.now()
                        CollectionItemTable.batchInsert(toAdd) { contentId ->
                            this[CollectionItemTable.collectionId] = collectionId
                            this[CollectionItemTable.contentId] = contentId
                            this[CollectionItemTable.addedAt] = now
                        }
                        processedCount += toAdd.size
                    }
                }
                ActionOption.REMOVE -> {
                    // Batch delete using a single query
                    val deleted = CollectionItemTable.deleteWhere {
                        (CollectionItemTable.collectionId eq collectionId) and
                        (CollectionItemTable.contentId inList contentIds)
                    }
                    processedCount += deleted
                }
            }
        }

        processedCount
    }
}