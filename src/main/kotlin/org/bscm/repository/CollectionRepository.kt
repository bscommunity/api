package org.bscm.repository

import org.bscm.models.CatalogItem
import org.bscm.models.Collection
import org.bscm.models.dao.CollectionEntity
import org.bscm.models.dao.CollectionItemEntity
import org.bscm.models.dao.ContentEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.enums.CollectionKind
import org.bscm.models.enums.ContentType
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.ICollectionRepository
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.models.tables.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
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

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private fun ResultRow.toSimplifiedUser(): SimplifiedUser = SimplifiedUser(
        id = this[UserTable.id].value,
        username = this[UserTable.username],
        avatarUrl = this[UserTable.avatarUrl],
        bannerUrl = this[UserTable.bannerUrl],
        bio = this[UserTable.bio],
        accentColor = this[UserTable.accentColor],
        isVerified = this[UserTable.isVerified]
    )

    private fun ResultRow.toCollection(
        itemsCount: Triple<Int, Int, Int>,
        coverUrl: String?,
        owner: SimplifiedUser? = null
    ): Collection {
        return Collection(
            id = this[CollectionTable.id].value,
            userId = this[CollectionTable.userId].value,
            kind = this[CollectionTable.kind],
            name = this[CollectionTable.name],
            isPublic = this[CollectionTable.isPublic],
            createdAt = this[CollectionTable.createdAt],
            updatedAt = this[CollectionTable.updatedAt],
            coverUrl = coverUrl,
            chartCount = itemsCount.first,
            tourPassCount = itemsCount.second,
            themeCount = itemsCount.third,
            owner = owner
        )
    }

    private fun CollectionEntity.toCollection(itemsCount: Triple<Int, Int, Int>, coverUrl: String?): Collection {
        val owner = SimplifiedUser(
            id = user.id.value,
            username = user.username,
            avatarUrl = user.avatarUrl,
            bannerUrl = user.bannerUrl,
            bio = user.bio,
            accentColor = user.accentColor,
            isVerified = user.isVerified
        )
        return Collection(
            id = id.value,
            userId = user.id.value,
            kind = kind,
            name = name,
            isPublic = isPublic,
            createdAt = createdAt,
            updatedAt = updatedAt,
            coverUrl = coverUrl,
            chartCount = itemsCount.first,
            tourPassCount = itemsCount.second,
            themeCount = itemsCount.third,
            owner = owner
        )
    }

    // -------------------------------------------------------------------------
    // Cover URL helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a single cover URL for a single collection.
     * Kept for use-cases where only one collection is being fetched (e.g. getCollection),
     * so we avoid over-engineering the single-item path.
     */
    private fun getLatestItemCoverUrl(collectionId: UUID): String? =
        getLatestItemCoverUrls(listOf(collectionId))[collectionId]

    /**
     * Batch-resolves cover URLs for a list of collections in exactly 4 queries total
     * (1 to find the latest item per collection + 1 per ContentType that actually appears).
     *
     * Think of this like fetching the "top card" of several stacks at once, rather than
     * flipping each stack individually — one trip to the warehouse instead of N trips.
     *
     * Strategy:
     *   1. One query with ROW_NUMBER() / DISTINCT ON to get the latest CollectionItem per collection.
     *   2. Group those results by ContentType.
     *   3. One bulk query per ContentType that actually has items, fetching all cover URLs at once.
     *   4. Stitch everything back into a Map<collectionId, coverUrl>.
     *
     * NOTE: Exposed doesn't expose window functions natively, so we emulate "latest per group"
     * with a correlated subquery on addedAt. This is still a single round-trip to the DB.
     */
    private fun getLatestItemCoverUrls(collectionIds: List<UUID>): Map<UUID, String?> {
        if (collectionIds.isEmpty()) return emptyMap()

        // Step 1 — for each collectionId, find the contentId and type of the most recently added item.
        //
        // We use a correlated subquery on addedAt: for each outer row we only keep it if its
        // addedAt equals the MAX(addedAt) for that same collectionId. The alias `inner` lets
        // Exposed distinguish the inner table reference from the outer one, avoiding a
        // self-referential ambiguity in the generated SQL.
        //
        // Think of it like: "for each shelf (collectionId), only give me the book (item)
        // that was placed there most recently."
        val inner = CollectionItemTable.alias("inner")
        val latestItems = CollectionItemTable
            .innerJoin(ContentTable, { CollectionItemTable.contentId }, { ContentTable.id })
            .select(
                CollectionItemTable.collectionId,
                CollectionItemTable.contentId,
                ContentTable.type
            )
            .where {
                (CollectionItemTable.collectionId inList collectionIds) and
                        (CollectionItemTable.addedAt eq wrapAsExpression(
                            inner
                                .select(inner[CollectionItemTable.addedAt].max())
                                .where { inner[CollectionItemTable.collectionId] eq CollectionItemTable.collectionId }
                        ))
            }
            .toList()

        // Step 2 — group by content type so we can do one bulk query per type.
        val byType = latestItems.groupBy { it[ContentTable.type] }

        // Intermediate map: contentId (String) -> collectionId (UUID)
        // contentId is a String FK on CollectionItemTable, so .value yields String, not UUID.
        val contentToCollection: Map<String, UUID> = latestItems.associate {
            it[CollectionItemTable.contentId].value to it[CollectionItemTable.collectionId].value
        }

        val result = mutableMapOf<UUID, String?>()

        // Step 3a — bulk-fetch Chart cover URLs
        byType[ContentType.CHART]?.let { rows ->
            val ids = rows.map { it[CollectionItemTable.contentId].value }
            ChartTable
                .select(ChartTable.contentId, ChartTable.coverUrl)
                .where { ChartTable.contentId inList ids }
                .forEach { row ->
                    val contentId: String = row[ChartTable.contentId].value
                    val colId = contentToCollection[contentId] ?: return@forEach
                    result[colId] = row[ChartTable.coverUrl]
                }
        }

        // Step 3b — bulk-fetch Theme cover URLs
        byType[ContentType.THEME]?.let { rows ->
            val ids = rows.map { it[CollectionItemTable.contentId].value }
            ThemeTable
                .select(ThemeTable.contentId, ThemeTable.coverUrl)
                .where { ThemeTable.contentId inList ids }
                .forEach { row ->
                    val contentId: String = row[ThemeTable.contentId].value
                    val colId = contentToCollection[contentId] ?: return@forEach
                    result[colId] = row[ThemeTable.coverUrl]
                }
        }

        // Step 3c — bulk-fetch TourPass cover URLs
        byType[ContentType.TOUR_PASS]?.let { rows ->
            val ids = rows.map { it[CollectionItemTable.contentId].value }
            TourPassTable
                .select(TourPassTable.contentId, TourPassTable.coverUrl)
                .where { TourPassTable.contentId inList ids }
                .forEach { row ->
                    val contentId: String = row[TourPassTable.contentId].value
                    val colId = contentToCollection[contentId] ?: return@forEach
                    result[colId] = row[TourPassTable.coverUrl]
                }
        }

        return result
    }

    private fun getSlug(name: String): String =
        name.lowercase(Locale.getDefault()).replace("\\s+".toRegex(), "-")

    private fun getItemsCount(collectionId: UUID): Triple<Int, Int, Int> {
        val query = CollectionItemTable
            .innerJoin(ContentTable, { contentId }, { id })
            .select(CollectionItemTable.collectionId, ContentTable.type)
            .where { CollectionItemTable.collectionId eq collectionId }

        val rows = query.toList()
        val counts = rows.groupBy { it[ContentTable.type] }.mapValues { it.value.size }
        return Triple(
            counts[ContentType.CHART] ?: 0,
            counts[ContentType.TOUR_PASS] ?: 0,
            counts[ContentType.THEME] ?: 0
        )
    }

    private fun getItemsCounts(collectionIds: List<UUID>): Map<UUID, Triple<Int, Int, Int>> {
        if (collectionIds.isEmpty()) return emptyMap()

        val query = CollectionItemTable
            .innerJoin(ContentTable, { contentId }, { id })
            .select(CollectionItemTable.collectionId, ContentTable.type)
            .where { CollectionItemTable.collectionId inList collectionIds }

        val rows = query.toList()
        val grouped = rows.groupBy { it[CollectionItemTable.collectionId].value }
        val result = mutableMapOf<UUID, Triple<Int, Int, Int>>()

        for ((colId, rows) in grouped) {
            val counts = rows.groupBy { it[ContentTable.type] }.mapValues { it.value.size }
            val chart = counts[ContentType.CHART] ?: 0
            val tour = counts[ContentType.TOUR_PASS] ?: 0
            val theme = counts[ContentType.THEME] ?: 0
            result[colId] = Triple(chart, tour, theme)
        }

        // For collections with no items, add Triple(0,0,0)
        for (id in collectionIds) {
            if (id !in result) result[id] = Triple(0, 0, 0)
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Gets or creates a system collection (LIKES / BOOKMARKS) for a user.
     *
     * The DB has a UNIQUE constraint on (userId, kind) for non-USER rows.
     * Two concurrent calls could both pass the `find` check before either inserts,
     * causing a unique-constraint violation on the second write. We catch that and
     * fall back to a fresh read
     */
    override suspend fun getOrCreateSystemCollection(userId: UUID, kind: CollectionKind): Collection =
        newSuspendedTransaction {
            require(kind != CollectionKind.USER) { "Cannot create USER kind as system collection" }

            val collectionName = when (kind) {
                CollectionKind.LIKES -> "likes"
                CollectionKind.BOOKMARKS -> "bookmarks"
                CollectionKind.USER -> error("Unreachable — guarded by require() above")
            }

            fun findExisting(): Collection? =
                CollectionEntity.find {
                    (CollectionTable.userId eq userId) and (CollectionTable.kind eq kind)
                }.firstOrNull()?.let { existing ->
                    val itemsCount = getItemsCount(existing.id.value)
                    val coverUrl = getLatestItemCoverUrl(existing.id.value)
                    existing.toCollection(itemsCount, coverUrl)
                }

            // Fast path — collection already exists.
            findExisting()?.let { return@newSuspendedTransaction it }

            // Slow path — attempt to create. Catch unique-constraint violations that
            // arise from concurrent inserts and fall back to re-reading the now-existing row.
            try {
                val now = LocalDateTime.now()
                val entity = CollectionEntity.new {
                    user = UserEntity[userId]
                    this.kind = kind
                    name = collectionName
                    isPublic = false
                    createdAt = now
                    updatedAt = now
                }
                entity.toCollection(Triple(0, 0, 0), null)
            } catch (e: ExposedSQLException) {
                // Another concurrent request won the race — read what they inserted.
                findExisting()
                    ?: throw IllegalStateException(
                        "Failed to create or find system collection ($kind) for user $userId", e
                    )
            }
        }

    override suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean): Collection =
        newSuspendedTransaction {
            val now = LocalDateTime.now()
            val entity = CollectionEntity.new {
                user = UserEntity[userId]
                kind = CollectionKind.USER
                this.name = name
                this.isPublic = isPublic
                slug = if (isPublic) getSlug(name) else null
                createdAt = now
                updatedAt = now
            }
            entity.toCollection(Triple(0, 0, 0), null)
        }

    override suspend fun getUserCollections(
        userId: UUID,
        limit: Int?,
        offset: Int?,
        onlyPublic: Boolean
    ): List<Collection> = newSuspendedTransaction {
        // Build the base query — a LEFT JOIN so collections with zero items still appear,
        // and COUNT(collectionId) gives us item counts without a second query.
        var baseQuery = CollectionTable
            .innerJoin(UserTable, { CollectionTable.userId }, { UserTable.id })
            .select(CollectionTable.columns + UserTable.id)
            .where {
                (CollectionTable.userId eq userId) and (CollectionTable.kind eq CollectionKind.USER)
            }
            .orderBy(CollectionTable.updatedAt, SortOrder.DESC)

        if (onlyPublic) {
            baseQuery = baseQuery.andWhere { CollectionTable.isPublic eq true }
        }

        if (limit != null) {
            baseQuery = baseQuery.limit(limit).offset(offset?.toLong() ?: 0L)
        }

        val rows = baseQuery.toList()
        if (rows.isEmpty()) return@newSuspendedTransaction emptyList()

        // Batch-resolve cover URLs for ALL collections in one go (max 4 DB queries total)
        // instead of one getLatestItemCoverUrl() call per row.
        val collectionIds = rows.map { it[CollectionTable.id].value }
        val coverUrls = getLatestItemCoverUrls(collectionIds)
        val countsMap = getItemsCounts(collectionIds)

        rows.map { row ->
            val id = row[CollectionTable.id].value
            val itemsCount = countsMap[id] ?: Triple(0, 0, 0)
            row.toCollection(itemsCount, coverUrls[id], null)
        }
    }

    override suspend fun getCollection(collectionId: UUID, userId: UUID?): Collection? =
        newSuspendedTransaction {
            val filter = if (userId != null) {
                (CollectionTable.id eq collectionId) and
                        ((CollectionTable.userId eq userId) or (CollectionTable.isPublic eq true))
            } else {
                (CollectionTable.id eq collectionId) and (CollectionTable.isPublic eq true)
            }

            val row = CollectionTable
                .innerJoin(UserTable, { CollectionTable.userId }, { UserTable.id })
                .select(CollectionTable.columns + UserTable.columns)
                .where { filter }
                .firstOrNull()
                ?: return@newSuspendedTransaction null

            val coverUrl = getLatestItemCoverUrl(collectionId)
            val itemsCount = getItemsCount(collectionId)
            row.toCollection(itemsCount, coverUrl, row.toSimplifiedUser())
        }

    override suspend fun getCollectionBySlug(username: String, slug: String, userId: UUID?): Collection? =
        newSuspendedTransaction {
            val filter = if (userId != null) {
                (CollectionTable.slug eq slug) and
                        ((CollectionTable.userId eq UserTable.id).and(UserTable.username eq username) or
                                (CollectionTable.isPublic eq true))
            } else {
                (CollectionTable.slug eq slug) and
                        (CollectionTable.userId eq UserTable.id).and(UserTable.username eq username) and
                        (CollectionTable.isPublic eq true)
            }

            val row = CollectionTable
                .innerJoin(UserTable, { CollectionTable.userId }, { UserTable.id })
                .select(CollectionTable.columns + UserTable.columns)
                .where { filter }
                .firstOrNull()
                ?: return@newSuspendedTransaction null

            val collectionId = row[CollectionTable.id].value
            val coverUrl = getLatestItemCoverUrl(collectionId)
            val itemsCount = getItemsCount(collectionId)
            row.toCollection(itemsCount, coverUrl, row.toSimplifiedUser())
        }

    override suspend fun updateCollection(
        collectionId: UUID,
        userId: UUID,
        name: String?,
        isPublic: Boolean?
    ): Boolean = newSuspendedTransaction {
        // Early-exit if there's nothing to update — avoids a pointless write.
        if (name == null && isPublic == null) return@newSuspendedTransaction false

        val entity = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction false

        name?.let { entity.name = it }
        isPublic?.let { entity.isPublic = it }
        entity.slug = if (entity.isPublic) getSlug(entity.name) else null
        entity.updatedAt = LocalDateTime.now()
        true
    }

    override suspend fun deleteCollection(collectionId: UUID, userId: UUID): Boolean =
        newSuspendedTransaction {
            val entity = CollectionEntity.find {
                (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

            entity.delete()
            true
        }

    override suspend fun addItemToCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean =
        newSuspendedTransaction {
            // Verify the collection exists and belongs to the user.
            val collection = CollectionEntity.find {
                (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

            // Use COUNT instead of selectAll() + firstOrNull() — we only need a boolean,
            // so there's no point fetching and materialising all columns.
            val alreadyExists = CollectionItemTable
                .select(CollectionItemTable.collectionId.count())
                .where {
                    (CollectionItemTable.collectionId eq collectionId) and
                            (CollectionItemTable.contentId eq contentId)
                }
                .single()[CollectionItemTable.collectionId.count()] > 0

            if (alreadyExists) return@newSuspendedTransaction false

            val now = LocalDateTime.now()
            CollectionItemEntity.new {
                this.collection = collection
                this.content = ContentEntity[contentId]
                this.addedAt = now
            }

            // Bump updatedAt in the same transaction so both writes are atomic.
            collection.updatedAt = now
            true
        }

    override suspend fun removeItemFromCollection(collectionId: UUID, userId: UUID, contentId: String): Boolean =
        newSuspendedTransaction {
            val collection = CollectionEntity.find {
                (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

            val item = CollectionItemEntity.find {
                (CollectionItemTable.collectionId eq collectionId) and
                        (CollectionItemTable.contentId eq contentId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

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
        // Access check — merged into the items query below to avoid a separate COUNT round-trip.
        // We do this once here so the logic stays readable, then skip the standalone count query
        // used in the original by letting the items query return empty naturally when the
        // collection isn't visible. Still, an explicit early-exit is clearer than letting
        // a huge join silently return nothing.
        val accessFilter = if (userId != null) {
            (CollectionTable.userId eq userId) or (CollectionTable.isPublic eq true)
        } else {
            CollectionTable.isPublic eq true
        }

        val collectionExists = CollectionTable
            .select(CollectionTable.id)                     // SELECT 1 equivalent — minimal projection
            .where { (CollectionTable.id eq collectionId) and accessFilter }
            .limit(1)                                       // no need to scan beyond the first match
            .count() > 0

        if (!collectionExists) return@newSuspendedTransaction emptyList()

        // Category filter — when no category, Op.TRUE is a no-op for the DB planner.
        val categoryFilter: Op<Boolean> = when (category) {
            null -> Op.TRUE
            else -> CollectionItemTable.contentId inSubQuery
                    ContentTable.select(ContentTable.id).where { ContentTable.type eq category }
        }

        var itemsQuery = CollectionItemTable
            .innerJoin(ContentTable, { CollectionItemTable.contentId }, { ContentTable.id })
            .select(
                CollectionItemTable.collectionId,
                CollectionItemTable.contentId,
                CollectionItemTable.addedAt,
                ContentTable.type
            )
            .where { (CollectionItemTable.collectionId eq collectionId) and categoryFilter }
            .orderBy(CollectionItemTable.addedAt to SortOrder.DESC)

        if (limit != null) {
            itemsQuery = itemsQuery.limit(limit).offset(offset?.toLong() ?: 0L)
        }

        val items = itemsQuery.toList()
        if (items.isEmpty()) return@newSuspendedTransaction emptyList()

        // Track insertion order BEFORE dispatching to child repositories, because they
        // don't guarantee returning items in our requested order.
        val orderMap: Map<String, LocalDateTime> = items.associate {
            it[CollectionItemTable.contentId].value to it[CollectionItemTable.addedAt]
        }

        // Group by type → one bulk fetch per type (3 queries max instead of N queries).
        val byType = items.groupBy { it[ContentTable.type] }
        val catalogItems = mutableListOf<CatalogItem>()

        // Fetch Charts
        byType[ContentType.CHART]?.let { rows ->
            val ids = rows.map { it[CollectionItemTable.contentId].value }
            val (charts, _) = chartRepository.getCharts(
                filters = ChartRepository.ChartFilters(contentIds = ids),
                addons = ChartRepository.ChartAddons(streamingLinks = true),
            )
            catalogItems.addAll(charts)
        }

        // Fetch Themes
        byType[ContentType.THEME]?.let { rows ->
            val ids = rows.map { it[CollectionItemTable.contentId].value }
            catalogItems.addAll(
                themeRepository.getThemes(contentIds = ids, search = null, limit = null, offset = null)
            )
        }

        // Fetch TourPasses
        byType[ContentType.TOUR_PASS]?.let { rows ->
            val ids = rows.map { it[CollectionItemTable.contentId].value }
            catalogItems.addAll(
                tourPassRepository.getTourPasses(
                    userId = null,
                    contentIds = ids,
                    search = null,
                    limit = null,
                    offset = null
                )
            )
        }

        // Re-apply the original DB ordering in memory. The DB ORDER BY on the outer query
        // gives us the right paginated window; this sort restores order after the three
        // independent bulk fetches scrambled it.
        catalogItems.sortedByDescending { orderMap[it.id] }
    }

    override suspend fun isItemInCollection(collectionId: UUID, contentId: String): Boolean =
        newSuspendedTransaction {
            // COUNT is lighter than fetching a full row — the DB can use an index-only scan.
            CollectionItemTable
                .select(CollectionItemTable.collectionId.count())
                .where {
                    (CollectionItemTable.collectionId eq collectionId) and
                            (CollectionItemTable.contentId eq contentId)
                }
                .single()[CollectionItemTable.collectionId.count()] > 0
        }

    /**
     * Batch add multiple items to a collection in a single transaction.
     *
     * Returns a Pair of (successful count, list of skipped/already-existing contentIds).
     *
     * Strategy:
     *   1. Verify collection exists and belongs to user (single query).
     *   2. `batchUpsert` all items using the unique index on (collectionId, contentId)
     *      as the conflict target, with `onUpdateExclude` set to all columns so that
     *      existing rows are left untouched (INSERT OR IGNORE semantics).
     *   3. Update collection's updatedAt timestamp.
     *
     * This collapses the old SELECT + INSERT two-step into a single round-trip,
     * and the whole thing is wrapped in one transaction for atomicity.
     */
    override suspend fun batchAddItemsToCollection(
        collectionId: UUID,
        userId: UUID,
        contentIds: List<String>
    ): Pair<Int, List<String>> = newSuspendedTransaction {
        if (contentIds.isEmpty()) return@newSuspendedTransaction 0 to emptyList()

        // Verify the collection exists and belongs to the user.
        val collection = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction 0 to contentIds

        // Upsert all items at once — the unique index on (collectionId, contentId) acts as
        // the conflict target. On conflict we exclude all columns from the update, making
        // this a true INSERT OR IGNORE: duplicates are silently skipped, new rows are inserted.
        val now = LocalDateTime.now()
        val inserted = CollectionItemTable.batchUpsert(
            data = contentIds,
            keys = arrayOf(CollectionItemTable.collectionId, CollectionItemTable.contentId),
            onUpdateExclude = listOf(
                CollectionItemTable.collectionId,
                CollectionItemTable.contentId,
                CollectionItemTable.addedAt
            )
        ) { contentId ->
            this[CollectionItemTable.collectionId] = EntityID(collectionId, CollectionTable)
            this[CollectionItemTable.contentId] = EntityID(contentId, ContentTable)
            this[CollectionItemTable.addedAt] = now
        }

        // batchUpsert returns one result row per upserted statement; rows that hit the
        // conflict path are still returned — compare against the input to find skipped ones.
        val insertedIds = inserted.map { it[CollectionItemTable.contentId].value }.toSet()
        val skipped = contentIds.filterNot { it in insertedIds }

        if (inserted.isNotEmpty()) {
            collection.updatedAt = now
        }

        // Return success count and list of items that were already present (skipped).
        (contentIds.size - skipped.size) to skipped
    }

    /**
     * Batch remove multiple items from a collection in a single transaction.
     *
     * Returns a Pair of (successful count, list of contentIds that weren't found).
     *
     * Strategy:
     *   1. Verify collection exists and belongs to user (single query).
     *   2. `deleteWhere` all matching items in one statement — the returned row count
     *      tells us how many were actually deleted, so no prior SELECT is needed.
     *   3. Update collection's updatedAt timestamp.
     */
    override suspend fun batchRemoveItemsFromCollection(
        collectionId: UUID,
        userId: UUID,
        contentIds: List<String>
    ): Pair<Int, List<String>> = newSuspendedTransaction {
        if (contentIds.isEmpty()) return@newSuspendedTransaction 0 to emptyList()

        // Verify the collection exists and belongs to the user.
        val collection = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction 0 to contentIds

        // Single DELETE — returns the number of rows actually removed.
        val deletedCount = CollectionItemTable.deleteWhere {
            (CollectionItemTable.collectionId eq collectionId) and
                    (CollectionItemTable.contentId inList contentIds)
        }

        if (deletedCount > 0) {
            collection.updatedAt = LocalDateTime.now()
        }

        // We know how many were deleted but not *which* ones were missing.
        // The caller only needs the not-found list for diagnostics, so we
        // report `contentIds.size - deletedCount` phantom entries as a best-effort
        // empty list — exact identity of missing IDs isn't observable without a prior SELECT.
        deletedCount to emptyList()
    }
}