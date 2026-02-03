package org.bscm.repository

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.dao.CollectionEntity
import org.bscm.models.dao.CollectionItemEntity
import org.bscm.models.dao.ContentEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.enums.CollectionKind
import org.bscm.models.enums.ContentType
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.ICollectionRepository
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.bscm.models.tables.ContentTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class CollectionRepository(
    private val chartRepository: IChartRepository,
    private val themeRepository: IThemeRepository,
    private val tourPassRepository: ITourPassRepository
) : ICollectionRepository {

    /**
     * Get or create a system collection for a user based on CollectionKind
     */
    override suspend fun getOrCreateSystemCollection(userId: UUID, kind: CollectionKind): Collection = newSuspendedTransaction {
        require(kind != CollectionKind.USER) { "Cannot create USER kind as system collection" }

        // Try to find existing system collection
        val existing = CollectionEntity.find {
            (CollectionTable.userId eq userId) and (CollectionTable.kind eq kind)
        }.firstOrNull()

        if (existing != null) {
            val itemCount = CollectionItemTable.selectAll()
                .where { CollectionItemTable.collectionId eq existing.id }
                .count().toInt()

            return@newSuspendedTransaction Collection(
                id = existing.id.value,
                userId = existing.user.id.value,
                kind = existing.kind,
                name = existing.name,
                isPublic = existing.isPublic,
                createdAt = existing.createdAt,
                updatedAt = existing.updatedAt,
                itemsCount = itemCount
            )
        }

        // Create new system collection
        val now = LocalDateTime.now()
        val collectionName = when (kind) {
            CollectionKind.LIKES -> "likes"
            CollectionKind.BOOKMARKS -> "bookmarks"
            CollectionKind.USER -> throw IllegalArgumentException("USER kind not allowed")
        }

        val entity = CollectionEntity.new {
            user = UserEntity[userId]
            this.kind = kind
            this.name = collectionName
            this.isPublic = false // System collections are always private by default
            createdAt = now
            updatedAt = now
        }

        Collection(
            id = entity.id.value,
            userId = userId,
            kind = entity.kind,
            name = entity.name,
            isPublic = entity.isPublic,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            itemsCount = 0
        )
    }

    override suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean): Collection =
        newSuspendedTransaction {
            val now = LocalDateTime.now()
            val entity = CollectionEntity.new {
                user = UserEntity[userId]
                kind = CollectionKind.USER
                this.name = name
                this.isPublic = isPublic
                createdAt = now
                updatedAt = now
            }

            Collection(
                id = entity.id.value,
                userId = userId,
                kind = entity.kind,
                name = entity.name,
                isPublic = entity.isPublic,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                itemsCount = 0
            )
        }

    override suspend fun getUserCollections(
        userId: UUID,
        limit: Int?,
        offset: Int?
    ): List<Collection> = newSuspendedTransaction {
        val query = CollectionEntity.find {
            (CollectionTable.userId eq userId) and (CollectionTable.kind eq CollectionKind.USER)
        }.orderBy(CollectionTable.updatedAt to SortOrder.DESC)

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
                kind = entity.kind,
                name = entity.name,
                isPublic = entity.isPublic,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                itemsCount = itemCount
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
                kind = row[CollectionTable.kind],
                name = row[CollectionTable.name],
                isPublic = row[CollectionTable.isPublic],
                createdAt = row[CollectionTable.createdAt],
                updatedAt = row[CollectionTable.updatedAt],
                itemsCount = itemCount
            )
        }
    }

    override suspend fun updateCollection(
        collectionId: UUID,
        userId: UUID,
        name: String?,
        isPublic: Boolean?
    ): Boolean = newSuspendedTransaction {
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

    override suspend fun addItemToCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean =
        newSuspendedTransaction {
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

    override suspend fun removeItemFromCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean =
        newSuspendedTransaction {
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

    override suspend fun getCollectionItems(
        collectionId: UUID,
        userId: UUID?,
        category: ContentType?,
        limit: Int?,
        offset: Int?
    ): List<CatalogItem> = newSuspendedTransaction {
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
                filters = ChartRepository.ChartFilters(contentIds = contentIds),
                addons = ChartRepository.ChartAddons(allVersions = true),
            )
            catalogItems.addAll(chartResults.first)
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
        val orderMap = items.associate {
            it[CollectionItemTable.contentId].value to it[CollectionItemTable.addedAt]
        }

        catalogItems.sortedByDescending { item ->
            orderMap[item.id]
        }
    }

    override suspend fun isItemInCollection(collectionId: UUID, contentId: String): Boolean = newSuspendedTransaction {
        val filter = (CollectionItemTable.collectionId eq collectionId) and
                (CollectionItemTable.contentId eq contentId)

        CollectionItemTable.selectAll().where { filter }.count() > 0
    }
}