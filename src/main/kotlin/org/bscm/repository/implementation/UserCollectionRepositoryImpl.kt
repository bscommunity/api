package org.bscm.repository.implementation

import org.bscm.models.*
import org.bscm.models.Collection
import org.bscm.models.dao.*
import org.bscm.models.enums.ContentType
import org.bscm.models.tables.*
import org.bscm.repository.UserCollectionRepository
import org.bscm.repository.implementation.ContributorRepositoryImpl.Companion.contributorEntityToContributor
import org.bscm.repository.implementation.VersionRepositoryImpl.Companion.versionEntityToVersion
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class UserCollectionRepositoryImpl(
    private val chartRepository: ChartRepository,
    private val themeRepository: ThemeRepository,
    private val tourPassRepository: TourPassRepository
) : UserCollectionRepository {

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

    override suspend fun getCollection(collectionId: ULong, userId: UUID?): Collection? = newSuspendedTransaction {
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

    override suspend fun updateCollection(collectionId: ULong, userId: UUID, name: String?, isPublic: Boolean?): Boolean =
        newSuspendedTransaction {
            val entity = CollectionEntity.find {
                (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

            name?.let { entity.name = it }
            isPublic?.let { entity.isPublic = it }
            entity.updatedAt = LocalDateTime.now()
            true
        }

    override suspend fun deleteCollection(collectionId: ULong, userId: UUID): Boolean = newSuspendedTransaction {
        val entity = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction false

        entity.delete()
        true
    }

    override suspend fun addItemToCollection(collectionId: ULong, userId: UUID, contentId: ULong): Boolean =
        newSuspendedTransaction {
            // Verificar se a coleção existe e pertence ao usuário
            val collection = CollectionEntity.find {
                (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

            // Verificar se o item já existe na coleção
            val existingFilter = (CollectionItemTable.collectionId eq collectionId) and
                    Op.TRUE

            val existing = CollectionItemTable.selectAll().where { existingFilter }.firstOrNull()
            if (existing != null) return@newSuspendedTransaction false

            // Adicionar o item
            CollectionItemEntity.new {
                this.collection = collection
                this.content = ContentEntity[contentId]
                this.addedAt = LocalDateTime.now()
            }

            // Atualizar timestamp da coleção
            collection.updatedAt = LocalDateTime.now()
            true
        }

    override suspend fun removeItemFromCollection(collectionId: ULong, userId: UUID, contentId: ULong): Boolean =
        newSuspendedTransaction {
            // Verificar se a coleção pertence ao usuário
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
        collectionId: ULong,
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
            val chartIds = chartItems.map { it[CollectionItemTable.contentId].value }
            val chartResults = fetchChartsWithAllData(chartIds)
            catalogItems.addAll(chartResults)
        }

        // Fetch Themes
        itemsByType[ContentType.THEME]?.let { themeItems ->
            val themeIds = themeItems.map { it[CollectionItemTable.contentId].value }
            val themeResults = fetchThemes(themeIds)
            catalogItems.addAll(themeResults)
        }

        // Fetch TourPasses with charts
        itemsByType[ContentType.TOUR_PASS]?.let { tourPassItems ->
            val tourPassIds = tourPassItems.map { it[CollectionItemTable.contentId].value }
            val tourPassResults = fetchTourPassesWithCharts(tourPassIds)
            catalogItems.addAll(tourPassResults)
        }

        // Restore original order from collection (by addedAt)
        val orderMap = items.mapIndexed { index, item -> 
            item[CollectionItemTable.contentId].value to index 
        }.toMap()
        
        catalogItems.sortedBy { item ->
            val contentId = item.id.toULong()
            orderMap[contentId] ?: Int.MAX_VALUE
        }
    }

    private fun fetchChartsWithAllData(chartIds: List<ULong>): List<Chart> {
        if (chartIds.isEmpty()) return emptyList()

        // Build a comprehensive query with all necessary joins
        val query = ChartTable
            .leftJoin(VersionTable, { ChartTable.id }, { VersionTable.chartId })
            .leftJoin(ContributorTable, { ChartTable.id }, { ContributorTable.chartId })
            .leftJoin(UserTable, { ContributorTable.userId }, { UserTable.id })
            .leftJoin(ChartStreamingLinkTable, { ChartTable.id }, { ChartStreamingLinkTable.chartId })
            .leftJoin(StreamingLinkTable, { ChartStreamingLinkTable.streamingLinkId }, { StreamingLinkTable.id })
            .selectAll()
            .where { ChartTable.id inList chartIds }

        val results = query.toList()
        return processChartResults(results)
    }

    private fun processChartResults(results: List<ResultRow>): List<Chart> {
        val groupedByChartId = results.groupBy { it[ChartTable.id].value }

        return groupedByChartId.map { (chartId, rows) ->
            val chartEntity = ChartEntity.wrapRow(rows.first())

            val streamingLinks = rows.mapNotNull { row ->
                row.getOrNull(StreamingLinkTable.id)?.let {
                    row.getOrNull(StreamingLinkTable.url)?.let {
                        StreamingLinkEntity.wrapRow(row)
                    }
                }
            }.distinctBy { it.id.value }.map { daoToStreamingLink(it) }

            val versions = rows.mapNotNull { row ->
                row.getOrNull(VersionTable.id)?.let {
                    VersionEntity.wrapRow(row)
                }
            }.distinctBy { it.id.value }.map { versionEntityToVersion(it) }

            val contributors = rows.mapNotNull { row ->
                row.getOrNull(ContributorTable.userId)?.let {
                    row.getOrNull(UserTable.id)?.let {
                        val contributor = ContributorEntity.wrapRow(row)
                        val user = UserEntity.wrapRow(row)
                        contributorEntityToContributor(contributor, user)
                    }
                }
            }.distinctBy { "${it.userId}-${it.role}" }

            Chart(
                id = chartEntity.id.value.toString(),
                shareId = chartEntity.shareId,
                artist = chartEntity.artist,
                track = chartEntity.track,
                album = chartEntity.album,
                genre = chartEntity.genre,
                trackUrls = streamingLinks,
                trackPreviewUrl = chartEntity.trackPreviewUrl,
                versions = versions,
                contributors = contributors,
                latestVersion = versions.maxByOrNull { it.publishedAt },
                coverUrl = chartEntity.coverUrl,
                isPublic = chartEntity.isPublic,
                isFeatured = chartEntity.isFeatured,
                downloadsSum = versions.sumOf { it.downloadsAmount },
                latestPublishedAt = versions.maxByOrNull { it.publishedAt }?.publishedAt ?: LocalDateTime.now()
            )
        }
    }

    private fun fetchThemes(themeIds: List<ULong>): List<Theme> {
        if (themeIds.isEmpty()) return emptyList()

        return ThemeTable.selectAll()
            .where { ThemeTable.id inList themeIds }
            .map { row ->
                val entity = ThemeEntity.wrapRow(row)
                Theme(
                    id = entity.id.value.toString(),
                    shareId = entity.shareId,
                    name = entity.name,
                    replaces = entity.replaces,
                    previewUrl = entity.previewUrl,
                    coverUrl = entity.coverUrl,
                    isPublic = entity.isPublic,
                    isFeatured = entity.isFeatured,
                    downloadsSum = entity.downloadsSum,
                    latestPublishedAt = entity.latestPublishedAt ?: LocalDateTime.now()
                )
            }
    }

    private fun fetchTourPassesWithCharts(tourPassIds: List<ULong>): List<TourPass> {
        if (tourPassIds.isEmpty()) return emptyList()

        // Fetch TourPasses with their associated charts
        val tourPassQuery = TourPassTable
            .leftJoin(TourPassChartTable, { TourPassTable.id }, { TourPassChartTable.tourPassId })
            .leftJoin(ChartTable, { TourPassChartTable.chartId }, { ChartTable.id })
            .selectAll()
            .where { TourPassTable.id inList tourPassIds }

        val results = tourPassQuery.toList()
        val groupedByTourPassId = results.groupBy { it[TourPassTable.id].value }

        return groupedByTourPassId.map { (tourPassId, rows) ->
            val tourPassEntity = TourPassEntity.wrapRow(rows.first())

            val charts = rows.mapNotNull { row ->
                row.getOrNull(ChartTable.id)?.let {
                    val chartEntity = ChartEntity.wrapRow(row)
                    Chart(
                        id = chartEntity.id.value.toString(),
                        shareId = chartEntity.shareId,
                        artist = chartEntity.artist,
                        track = chartEntity.track,
                        album = chartEntity.album,
                        genre = chartEntity.genre,
                        trackUrls = emptyList(), // Not loading streaming links for tour pass charts
                        trackPreviewUrl = chartEntity.trackPreviewUrl,
                        versions = emptyList(), // Not loading versions for tour pass charts
                        contributors = emptyList(), // Not loading contributors for tour pass charts
                        latestVersion = null,
                        coverUrl = chartEntity.coverUrl,
                        isPublic = chartEntity.isPublic,
                        isFeatured = chartEntity.isFeatured,
                        downloadsSum = chartEntity.downloadsSum,
                        latestPublishedAt = chartEntity.latestPublishedAt ?: LocalDateTime.now()
                    )
                }
            }.distinctBy { it.id }

            TourPass(
                id = tourPassEntity.id.value.toString(),
                shareId = tourPassEntity.shareId,
                name = tourPassEntity.name,
                artist = tourPassEntity.artist,
                charts = charts,
                coverUrl = tourPassEntity.coverUrl,
                isPublic = tourPassEntity.isPublic,
                isFeatured = tourPassEntity.isFeatured,
                downloadsSum = tourPassEntity.downloadsSum,
                latestPublishedAt = tourPassEntity.latestPublishedAt ?: LocalDateTime.now()
            )
        }
    }

    private fun daoToStreamingLink(entity: StreamingLinkEntity): StreamingLink = StreamingLink(
        platform = entity.platform,
        url = entity.url,
    )

    override suspend fun isItemInCollection(collectionId: ULong, contentId: ULong): Boolean =
        newSuspendedTransaction {
            val filter = (CollectionItemTable.collectionId eq collectionId) and
                    (CollectionItemTable.contentId eq contentId)

            CollectionItemTable.selectAll().where { filter }.count() > 0
        }

    override suspend fun batchProcessInteractions(
        userId: UUID,
        interactions: List<Pair<ULong, Boolean>>
    ): Int = newSuspendedTransaction {
        var processedCount = 0

        for ((contentId, isAdd) in interactions) {
            // Find or create a default collection for the user (e.g., "Likes")
            val collection = CollectionEntity.find {
                (CollectionTable.userId eq userId) and (CollectionTable.name eq "Likes")
            }.firstOrNull() ?: CollectionEntity.new {
                user = UserEntity[userId]
                name = "Likes"
                isPublic = false
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }

            val existingFilter = (CollectionItemTable.collectionId eq collection.id) and
                    (CollectionItemTable.contentId eq contentId)
            val existing = CollectionItemTable.selectAll().where { existingFilter }.firstOrNull()

            if (isAdd && existing == null) {
                // Add item to collection
                CollectionItemEntity.new {
                    this.collection = collection
                    this.content = ContentEntity[contentId]
                    this.addedAt = LocalDateTime.now()
                }
                processedCount++
            } else if (!isAdd && existing != null) {
                // Remove item from collection
                CollectionItemEntity.find { existingFilter }.firstOrNull()?.delete()
                processedCount++
            }
        }

        processedCount
    }
}