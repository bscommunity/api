package org.bscm.repository

import kotlinx.datetime.LocalDateTime
import org.bscm.models.Chart
import org.bscm.models.Contributor
import org.bscm.models.StreamingRef
import org.bscm.models.Version
import org.bscm.models.dao.*
import org.bscm.models.enums.CollectionKind
import org.bscm.models.mappers.VersionMapper
import org.bscm.models.tables.*
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.select
import java.util.*

class ChartResultAssembler(
    private val trackRepository: TrackRepository,
    private val catalogItemRepository: CatalogItemRepository,
    private val albumRepository: AlbumRepository,
) {
    data class ChartResult(
        val chart: ChartEntity,
        val catalogItem: CatalogItemEntity,
        val track: TrackEntity,
        val contributors: List<Contributor>,
        val streamingRefs: List<StreamingRef>,
        val latestVersion: VersionEntity? = null,
        val versionsCount: Int = 0,
        val bundleHash: String? = null,
        val likesCount: Int = 0,
        val bookmarksCount: Int = 0,
        val userStats: Pair<LocalDateTime?, LocalDateTime?>,
        // Read from the pre-fetched row — avoids a lazy per-chart users query
        val authorId: UUID? = null,
        val versions: List<Version> = emptyList(),
    )

    fun toChart(result: ChartResult): Chart = Chart(
        id = result.chart.id.value,
        status = result.catalogItem.status,
        visibility = result.catalogItem.visibility,
        isFeatured = result.catalogItem.isFeatured,
        downloadsSum = result.catalogItem.downloadsSum,
        likesCount = result.likesCount,
        bookmarksCount = result.bookmarksCount,
        contributors = result.contributors,
        createdAt = result.catalogItem.createdAt,
        publishedAt = result.catalogItem.publishedAt,
        updatedAt = result.catalogItem.updatedAt,
        likedAt = result.userStats.first,
        bookmarkedAt = result.userStats.second,
        previewVideoId = result.catalogItem.previewVideoId,
        discordChannelId = result.catalogItem.discordChannelId,
        discordMessageId = result.catalogItem.discordMessageId,
        authorId = result.authorId,
        track = trackRepository.toTrack(result.track, result.streamingRefs),
        versionsCount = result.versionsCount,
        bundleHash = result.bundleHash,
        difficulty = result.chart.difficulty,
        notesAmount = result.chart.notesAmount,
        effectsAmount = result.chart.effectsAmount,
        isDeluxe = result.chart.isDeluxe,
        isExplicit = result.chart.isExplicit,
        latestVersion = result.latestVersion?.let(VersionMapper::entityToVersion),
        versions = result.versions,
    )

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

    suspend fun processResultsInMemory(
        requestingUserId: UUID?,
        results: List<ResultRow>,
        includeStreamingRefs: Boolean,
    ): List<ChartResult> {
        val groupedByChartId: Map<String, List<ResultRow>> = results.groupBy { row: ResultRow ->
            row[ChartTable.id].value
        }
        val catalogIds = groupedByChartId.keys.toList()
        val userStats = catalogItemRepository.fetchUserStats(requestingUserId, catalogIds)
        val collectionStats = fetchAggregateStats(catalogIds)
        val contributorsByCatalogId = ContributorRepository.fetchContributorsByCatalogIds(catalogIds)

        val albumIds = groupedByChartId.values.flatten()
            .mapNotNull { it.getOrNull(AlbumTable.id)?.value }
            .distinct()
        val albumRefsByAlbumId = if (includeStreamingRefs && albumIds.isNotEmpty()) {
            albumRepository.getStreamingRefs(albumIds)
        } else emptyMap()

        return groupedByChartId.map { (_, rows) ->
            val chartEntity = ChartEntity.wrapRow(rows.first())
            val catalogItemEntity = CatalogItemEntity.wrapRow(rows.first())
            val trackEntity = TrackEntity.wrapRow(rows.first())

            rows.first().getOrNull(AlbumTable.id)?.let { AlbumEntity.wrapRow(rows.first()) }

            val streamingRefs = if (includeStreamingRefs) {
                val trackRefs = rows.mapNotNull { row ->
                    val platform = row.getOrNull(TrackStreamingRefTable.platform)
                    val externalId = row.getOrNull(TrackStreamingRefTable.externalId)
                    if (platform != null && externalId != null) {
                        StreamingRef(platform, StreamingPlatformUtils.buildUrl(platform, externalId))
                    } else null
                }.distinctBy { it.platform to it.url }

                // Fallback: if no track-level refs, use album-level refs
                if (trackRefs.isEmpty()) {
                    rows.first().getOrNull(AlbumTable.id)?.value?.let { albumId ->
                        val albumRefs = albumRefsByAlbumId[albumId].orEmpty()
                        if (albumRefs.isNotEmpty()) albumRefs else trackRefs
                    } ?: trackRefs
                } else trackRefs
            } else emptyList()

            ChartResult(
                chart = chartEntity,
                catalogItem = catalogItemEntity,
                track = trackEntity,
                contributors = contributorsByCatalogId[catalogItemEntity.id.value].orEmpty(),
                streamingRefs = streamingRefs,
                likesCount = collectionStats[catalogItemEntity.id.value]?.first ?: 0,
                bookmarksCount = collectionStats[catalogItemEntity.id.value]?.second ?: 0,
                userStats = userStats[catalogItemEntity.id.value] ?: Pair(null, null),
                authorId = rows.first()[CatalogItemTable.authorId]?.value,
            )
        }
    }
}
