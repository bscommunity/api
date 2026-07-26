package org.bscm.repository

import kotlinx.datetime.LocalDateTime
import org.bscm.models.Changelog
import org.bscm.models.Chart
import org.bscm.models.StreamingRef
import org.bscm.models.dao.*
import org.bscm.models.mappers.VersionMapper
import org.bscm.models.tables.*
import org.bscm.repository.ContributorRepository.Companion.contributorEntityToContributor
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.v1.core.ResultRow
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
        val contributors: List<Pair<ContributorEntity, UserEntity>>,
        val streamingRefs: List<StreamingRef>,
        val latestVersion: VersionEntity?,
        val userStats: Pair<LocalDateTime?, LocalDateTime?>,
        val changelog: List<Changelog>,
    )

    fun toChart(result: ChartResult): Chart = Chart(
        id = result.chart.id.value,
        status = result.catalogItem.status,
        visibility = result.catalogItem.visibility,
        isFeatured = result.catalogItem.isFeatured,
        downloadsSum = result.catalogItem.downloadsSum,
        contributors = result.contributors.map { contributorEntityToContributor(it.first, it.second) },
        createdAt = result.catalogItem.createdAt,
        publishedAt = result.catalogItem.publishedAt,
        updatedAt = result.catalogItem.updatedAt,
        likedAt = result.userStats.first,
        bookmarkedAt = result.userStats.second,
        previewVideoId = result.catalogItem.previewVideoId,
        discordChannelId = result.catalogItem.discordChannelId,
        discordMessageId = result.catalogItem.discordMessageId,
        authorId = result.catalogItem.author?.id?.value,
        track = trackRepository.toTrack(result.track, result.streamingRefs),
        versionsCount = result.catalogItem.versionsCount,
        difficulty = result.chart.difficulty,
        notesAmount = result.chart.notesAmount,
        effectsAmount = result.chart.effectsAmount,
        isDeluxe = result.chart.isDeluxe,
        isExplicit = result.chart.isExplicit,
        changelog = result.changelog,
        latestVersion = result.latestVersion?.let(VersionMapper::entityToVersion),
    )

    suspend fun processResultsInMemory(
        requestingUserId: UUID?,
        results: List<ResultRow>,
        includeStreamingRefs: Boolean,
        changelogs: Map<String, List<Changelog>> = emptyMap(),
    ): List<ChartResult> {
        val groupedByChartId: Map<String, List<ResultRow>> = results.groupBy { row: ResultRow ->
            row[ChartTable.id].value
        }
        val contentIds = groupedByChartId.keys.toList()
        val userStats = catalogItemRepository.fetchUserStats(requestingUserId, contentIds)

        return groupedByChartId.map { (_, rows) ->
            val chartEntity = ChartEntity.wrapRow(rows.first())
            val catalogItemEntity = CatalogItemEntity.wrapRow(rows.first())
            val trackEntity = TrackEntity.wrapRow(rows.first())

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
                    trackEntity.album?.id?.value?.let { albumId ->
                        val albumRefs = albumRepository.getStreamingRefs(albumId)
                        if (albumRefs.isNotEmpty()) {
                            albumRefs
                        } else trackRefs
                    } ?: trackRefs
                } else trackRefs
            } else emptyList()

            val contributors = rows.mapNotNull { row ->
                row.getOrNull(ContributorTable.userId)?.let { _ ->
                    row.getOrNull(UserTable.id)?.let { _ ->
                        val contributor = ContributorEntity.wrapRow(row)
                        val user = UserEntity.wrapRow(row)
                        contributor to user
                    }
                }
            }.distinctBy { it.first.id.value }

            val latestVersion = rows.firstNotNullOfOrNull { row ->
                row.getOrNull(VersionTable.id)?.let { _ -> VersionEntity.wrapRow(row) }
            }

            ChartResult(
                chart = chartEntity,
                catalogItem = catalogItemEntity,
                track = trackEntity,
                contributors = contributors,
                streamingRefs = streamingRefs,
                latestVersion = latestVersion,
                userStats = userStats[catalogItemEntity.id.value] ?: Pair(null, null),
                changelog = changelogs[catalogItemEntity.id.value] ?: emptyList(),
            )
        }
    }
}
