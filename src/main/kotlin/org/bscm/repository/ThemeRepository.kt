package org.bscm.repository

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.Theme
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.ThemeEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dao.VersionEntity
import org.bscm.models.enums.BeatstarThemeId
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.CollectionKind
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.bscm.models.tables.ThemeTable
import org.bscm.models.tables.VersionTable
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
    ): Theme {
        val catalogItem = CatalogItemEntity[entity.id.value]
        val id = entity.id.value
        return Theme(
            name = entity.name,
            replaces = entity.replaces,
            originalArtwork = entity.originalArtwork,
            displayArtUrl = storageService.themeDisplayUrl(id),
            previewUrl = entity.previewUrl,
            coverUrl = storageService.themeCoverUrl(id),
            contributors = emptyList(),
            likesCount = likesCount,
            bookmarksCount = bookmarksCount,
            createdAt = catalogItem.createdAt,
            publishedAt = catalogItem.publishedAt,
            updatedAt = catalogItem.updatedAt,
            likedAt = likedAt,
            bookmarkedAt = bookmarkedAt,
            id = id,
            type = CatalogItemType.THEME,
            status = catalogItem.status,
            visibility = catalogItem.visibility,
            isFeatured = catalogItem.isFeatured,
            downloadsSum = catalogItem.downloadsSum,
            previewVideoId = catalogItem.previewVideoId,
            discordChannelId = catalogItem.discordChannelId,
            discordMessageId = catalogItem.discordMessageId,
            authorId = catalogItem.author?.id?.value,
            versionsCount = versionsCount,
            latestVersion = latestVersionEntity?.let { org.bscm.models.mappers.VersionMapper.entityToVersion(it) },
            bundleHash = latestVersionEntity?.bundleHash,
        )
    }

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

        when {
            catalogIds != null && catalogIds.isNotEmpty() && search != null -> {
                query.where {
                    (ThemeTable.id inList catalogIds.map { EntityID(it, ThemeTable) }) and
                    (ThemeTable.name like "%${search}%")
                }
            }
            catalogIds != null && catalogIds.isNotEmpty() -> {
                query.where { ThemeTable.id inList catalogIds.map { EntityID(it, ThemeTable) } }
            }
            search != null -> {
                query.where { ThemeTable.name like "%${search}%" }
            }
        }

        query.orderBy(ThemeTable.id to SortOrder.DESC)
        val paged = query.limit(pageSize).offset(pageOffset.toLong()).toList()
            .map { ThemeEntity.wrapRow(it) }

        val themeIds = paged.map { it.id.value }
        val userStats = if (userId != null && themeIds.isNotEmpty()) {
            UserStatsUtils.fetchUserStats(userId, themeIds)
        } else emptyMap()

        val aggregateStats = fetchAggregateStats(themeIds)
        val versionData = enrichWithVersionData(themeIds)

        paged.map { entity ->
            val (likedAt, bookmarkedAt) = userStats[entity.id.value] ?: (null to null)
            val (vCount, vEntity) = versionData[entity.id.value] ?: (0 to null)
            val (likesCount, bookmarksCount) = aggregateStats[entity.id.value] ?: (0 to 0)
            themeEntityToTheme(entity, likedAt, bookmarkedAt, vCount, vEntity, likesCount, bookmarksCount)
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
            themeEntityToTheme(entity, likedAt, bookmarkedAt, vCount, vEntity, likesCount, bookmarksCount)
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
                this.author = UserEntity[userId]
                this.updatedAt = now
            }
        } else {
            CatalogItemEntity.new {
                this.type = CatalogItemType.THEME
                this.status = CatalogItemStatus.PUBLISHED
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

        themeEntityToTheme(theme)
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

        val countColumn = VersionTable.id.count()
        val counts = VersionTable
            .select(VersionTable.catalogItemId, countColumn)
            .where { VersionTable.catalogItemId inList catalogItemIds }
            .groupBy(VersionTable.catalogItemId)
            .toList()
            .associate { it[VersionTable.catalogItemId].value to it[countColumn].toInt() }

        val allVersions = VersionTable.selectAll()
            .where { VersionTable.catalogItemId inList catalogItemIds }
            .toList()

        val latestVersions = allVersions
            .groupBy { it[VersionTable.catalogItemId].value }
            .mapValues { (_, versions) ->
                versions.maxByOrNull { it[VersionTable.versionCode] }
            }
            .mapValues { (_, row) ->
                row?.let { VersionEntity.wrapRow(it) }
            }

        return catalogItemIds.associateWith { id ->
            Pair(counts[id] ?: 0, latestVersions[id])
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
