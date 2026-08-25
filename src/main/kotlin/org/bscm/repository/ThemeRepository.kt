package org.bscm.repository

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.Contributor
import org.bscm.models.Theme
import org.bscm.models.dao.*
import org.bscm.models.enums.*
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.tables.*
import org.bscm.storage.StorageService
import org.bscm.utils.UserStatsUtils
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.Clock

class ThemeRepository(
    private val catalogItemRepository: CatalogItemRepository,
    private val storageService: StorageService,
) : IThemeRepository {

    /**
     * Fetches aggregate likes/bookmarks counts for a batch of catalog items.
     * Returns a map of catalogId -> (likesCount, bookmarksCount).
     * Must be called within a transaction.
     */
    private fun fetchAggregateStats(catalogIds: List<String>): Map<String, Pair<Int, Int>> {
        if (catalogIds.isEmpty()) return emptyMap()

        val statsRows = CollectionItemTable
            .innerJoin(CollectionTable, { CollectionItemTable.collectionId }, { CollectionTable.id })
            .select(CollectionItemTable.catalogId, CollectionTable.kind, CollectionTable.userId)
            .where {
                (CollectionTable.kind inList listOf(CollectionKind.LIKES, CollectionKind.BOOKMARKS, CollectionKind.USER)) and
                    (CollectionItemTable.catalogId inList catalogIds)
            }
            .toList()

        return catalogIds.associateWith { catalogId ->
            val rows = statsRows.filter { it[CollectionItemTable.catalogId].value == catalogId }
            val likesCount = rows.filter { it[CollectionTable.kind] == CollectionKind.LIKES }
                .map { it[CollectionTable.userId] }
                .distinct()
                .size
            val bookmarksCount = rows.filter {
                it[CollectionTable.kind] == CollectionKind.BOOKMARKS || it[CollectionTable.kind] == CollectionKind.USER
            }
                .map { it[CollectionTable.userId] }
                .distinct()
                .size
            likesCount to bookmarksCount
        }
    }

    private fun themeEntityToTheme(
        entity: ThemeEntity,
        likedAt: LocalDateTime? = null,
        bookmarkedAt: LocalDateTime? = null,
        versionsCount: Int = 0,
        latestVersionEntity: VersionEntity? = null,
        likesCount: Int = 0,
        bookmarksCount: Int = 0,
        catalogRow: ResultRow? = null,
        contributors: List<Contributor> = emptyList(),
    ): Theme {
        val id = entity.id.value
        // When a pre-fetched catalog_items row is provided, use it directly to avoid
        // per-item DAO lookups and lazy author loads (N+1). Only the author ID is needed.
        val itemInfo = catalogRow?.let {
            CatalogItemFields(
                status = it[CatalogItemTable.status],
                visibility = it[CatalogItemTable.visibility],
                isFeatured = it[CatalogItemTable.isFeatured],
                previewVideoId = it[CatalogItemTable.previewVideoId],
                downloadsSum = it[CatalogItemTable.downloadsSum],
                discordChannelId = it[CatalogItemTable.discordChannelId],
                discordMessageId = it[CatalogItemTable.discordMessageId],
                createdAt = it[CatalogItemTable.createdAt],
                publishedAt = it[CatalogItemTable.publishedAt],
                updatedAt = it[CatalogItemTable.updatedAt],
                authorId = it[CatalogItemTable.authorId]?.value,
            )
        } ?: run {
            val catalogItem = CatalogItemEntity[id]
            CatalogItemFields(
                status = catalogItem.status,
                visibility = catalogItem.visibility,
                isFeatured = catalogItem.isFeatured,
                previewVideoId = catalogItem.previewVideoId,
                downloadsSum = catalogItem.downloadsSum,
                discordChannelId = catalogItem.discordChannelId,
                discordMessageId = catalogItem.discordMessageId,
                createdAt = catalogItem.createdAt,
                publishedAt = catalogItem.publishedAt,
                updatedAt = catalogItem.updatedAt,
                authorId = catalogItem.author?.id?.value,
            )
        }
        return Theme(
            name = entity.name,
            replaces = entity.replaces,
            originalArtwork = entity.originalArtwork,
            displayArtUrl = storageService.themeDisplayUrl(id),
            previewUrl = entity.previewUrl,
            coverUrl = storageService.themeCoverUrl(id),
            contributors = contributors,
            likesCount = likesCount,
            bookmarksCount = bookmarksCount,
            createdAt = itemInfo.createdAt,
            publishedAt = itemInfo.publishedAt,
            updatedAt = itemInfo.updatedAt,
            likedAt = likedAt,
            bookmarkedAt = bookmarkedAt,
            id = id,
            type = CatalogItemType.THEME,
            status = itemInfo.status,
            visibility = itemInfo.visibility,
            isFeatured = itemInfo.isFeatured,
            downloadsSum = itemInfo.downloadsSum,
            previewVideoId = itemInfo.previewVideoId,
            discordChannelId = itemInfo.discordChannelId,
            discordMessageId = itemInfo.discordMessageId,
            authorId = itemInfo.authorId,
            versionsCount = versionsCount,
            latestVersion = latestVersionEntity?.let { org.bscm.models.mappers.VersionMapper.entityToVersion(it) },
            bundleHash = latestVersionEntity?.bundleHash,
        )
    }

    private data class CatalogItemFields(
        val status: CatalogItemStatus,
        val visibility: Visibility,
        val isFeatured: Boolean,
        val previewVideoId: String?,
        val downloadsSum: Int,
        val discordChannelId: String?,
        val discordMessageId: String?,
        val createdAt: LocalDateTime,
        val publishedAt: LocalDateTime?,
        val updatedAt: LocalDateTime?,
        val authorId: UUID?,
    )

    override suspend fun getThemes(
        userId: UUID?,
        catalogIds: List<String>?,
        search: String?,
        limit: Int?,
        offset: Int?,
    ): List<Theme> = suspendTransaction {
        val pageSize = limit ?: 20
        val pageOffset = offset ?: 0

        val query = ThemeTable.selectAll()
        // When filtering by explicit catalogIds (e.g., a pre-paginated page from an
        // orchestrator), skip ordering/pagination — the caller already applied both.
        val isIdFiltered = catalogIds != null && catalogIds.isNotEmpty()

        when {
            isIdFiltered && search != null -> {
                query.where {
                    (ThemeTable.id inList catalogIds.map { EntityID(it, ThemeTable) }) and
                    (ThemeTable.name like "%${search}%")
                }
            }
            isIdFiltered -> {
                query.where { ThemeTable.id inList catalogIds.map { EntityID(it, ThemeTable) } }
            }
            search != null -> {
                query.where { ThemeTable.name like "%${search}%" }
            }
        }

        if (!isIdFiltered) {
            query.orderBy(ThemeTable.id to SortOrder.DESC)
            query.limit(pageSize).offset(pageOffset.toLong())
        }
        val paged = query.toList()
            .map { ThemeEntity.wrapRow(it) }

        val themeIds = paged.map { it.id.value }
        val userStats = if (userId != null && themeIds.isNotEmpty()) {
            UserStatsUtils.fetchUserStats(userId, themeIds)
        } else emptyMap()

        val aggregateStats = fetchAggregateStats(themeIds)
        val versionData = enrichWithVersionData(themeIds)
        val contributorsByCatalogId = ContributorRepository.fetchContributorsByCatalogIds(themeIds)

        // Batch-fetch catalog_items rows once instead of per-entity DAO lookups (N+1)
        val catalogRows = if (themeIds.isNotEmpty()) {
            CatalogItemTable.selectAll()
                .where { CatalogItemTable.id inList themeIds.map { EntityID(it, CatalogItemTable) } }
                .associateBy { it[CatalogItemTable.id].value }
        } else emptyMap()

        paged.map { entity ->
            val id = entity.id.value
            val (likedAt, bookmarkedAt) = userStats[id] ?: (null to null)
            val (vCount, vEntity) = versionData[id] ?: (0 to null)
            val (likesCount, bookmarksCount) = aggregateStats[id] ?: (0 to 0)
            themeEntityToTheme(
                entity,
                likedAt,
                bookmarkedAt,
                vCount,
                vEntity,
                likesCount,
                bookmarksCount,
                catalogRows[id],
                contributorsByCatalogId[id].orEmpty(),
            )
        }
    }

    override suspend fun getThemeById(id: String, userId: UUID?): Theme? = suspendTransaction {
        ThemeEntity.findById(id)?.let { entity ->
            val (likedAt, bookmarkedAt) = if (userId != null) {
                UserStatsUtils.fetchUserStats(userId, listOf(id))[id] ?: (null to null)
            } else (null to null)
            val (likesCount, bookmarksCount) = fetchAggregateStats(listOf(id))[id] ?: (0 to 0)
            val versionData = enrichWithVersionData(listOf(id))
            val (vCount, vEntity) = versionData[id] ?: (0 to null)
            val contributors = ContributorRepository.fetchContributorsByCatalogIds(listOf(id))[id].orEmpty()
            themeEntityToTheme(entity, likedAt, bookmarkedAt, vCount, vEntity, likesCount, bookmarksCount, contributors = contributors)
        }
    }

    override suspend fun createTheme(
        userId: UUID,
        name: String,
        replaces: String,
        originalArtwork: String?,
        previewUrl: String?,
        id: String?,
    ): Theme = suspendTransaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val catalogItem = if (id != null) {
            CatalogItemEntity.new(id) {
                this.type = CatalogItemType.THEME
                this.status = CatalogItemStatus.PUBLISHED
                this.publishedAt = now
                this.author = UserEntity[userId]
                this.updatedAt = now
            }
        } else {
            CatalogItemEntity.new {
                this.type = CatalogItemType.THEME
                this.status = CatalogItemStatus.PUBLISHED
                this.publishedAt = now
                this.author = UserEntity[userId]
                this.updatedAt = now
            }
        }

        val theme = ThemeEntity.new(catalogItem.id.value) {
            this.name = name
            this.replaces = BeatstarThemeId.fromBeatstarId(replaces)
            this.originalArtwork = originalArtwork
            this.previewUrl = previewUrl
        }

        ContributorEntity.new {
            this.catalogItem = catalogItem
            this.user = UserEntity[userId]
            this.role = ContributorRole.AUTHOR
        }

        val themeId = theme.id.value
        val contributors = ContributorRepository.fetchContributorsByCatalogIds(listOf(themeId))[themeId].orEmpty()

        themeEntityToTheme(theme, contributors = contributors)
    }

    override suspend fun updateTheme(
        id: String,
        userId: UUID,
        name: String?,
        replaces: String?,
        originalArtwork: String?,
        previewUrl: String?,
    ): Theme = suspendTransaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val entity = ThemeEntity.findByIdAndUpdate(id) { entity ->
            name?.let { entity.name = it }
            replaces?.let { entity.replaces = BeatstarThemeId.fromBeatstarId(it) }
            originalArtwork?.let { entity.originalArtwork = it }
            previewUrl?.let { entity.previewUrl = it }
        } ?: throw IllegalArgumentException("Theme $id not found")

        CatalogItemEntity.findByIdAndUpdate(id) {
            it.updatedAt = now
        }

        val (likesCount, bookmarksCount) = fetchAggregateStats(listOf(id))[id] ?: (0 to 0)
        themeEntityToTheme(entity, likesCount = likesCount, bookmarksCount = bookmarksCount)
    }

    override suspend fun deleteTheme(id: String, userId: UUID): Boolean = suspendTransaction {
        CatalogItemEntity.findById(id)?.delete() ?: return@suspendTransaction false
        true
    }

    private fun enrichWithVersionData(catalogItemIds: List<String>): Map<String, Pair<Int, VersionEntity?>> {
        if (catalogItemIds.isEmpty()) return emptyMap()

        // Single pass: fetch version rows once, derive both counts and latest in memory
        val allVersions = VersionTable.selectAll()
            .where { VersionTable.catalogItemId inList catalogItemIds }
            .toList()
            .groupBy { it[VersionTable.catalogItemId].value }

        return catalogItemIds.associateWith { id ->
            val versions = allVersions[id].orEmpty()
            val latestRow = versions.maxByOrNull { it[VersionTable.versionCode] }
            Pair(versions.size, latestRow?.let { VersionEntity.wrapRow(it) })
        }
    }

    override suspend fun countThemes(search: String?): Int = suspendTransaction {
        val query = ThemeTable.select(ThemeTable.id.count())
        search?.let { s ->
            query.where { ThemeTable.name like "%${s}%" }
        }
        query.toList().first()[ThemeTable.id.count()].toInt()
    }

    override suspend fun updateDiscordCoordinates(catalogItemId: String, channelId: String, messageId: String) = suspendTransaction {
        catalogItemRepository.updateDiscordCoordinates(catalogItemId, channelId, messageId)
    }

    override suspend fun findThemeByBundleHash(hash: String): Theme? = suspendTransaction {
        val versionRow = VersionTable.selectAll()
            .where { VersionTable.bundleHash eq hash }
            .firstOrNull() ?: return@suspendTransaction null

        val catalogItemId = versionRow[VersionTable.catalogItemId].value
        val entity = ThemeEntity.findById(catalogItemId) ?: return@suspendTransaction null
        themeEntityToTheme(entity)
    }
}
