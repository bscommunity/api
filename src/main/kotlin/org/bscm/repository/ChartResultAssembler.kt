package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.StreamingRef
import org.bscm.models.dao.*
import org.bscm.models.mappers.VersionMapper
import org.bscm.models.tables.*
import org.bscm.repository.ContributorRepository.Companion.contributorEntityToContributor
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.sql.ResultRow
import java.time.LocalDateTime
import java.util.*

class ChartResultAssembler(
    private val trackRepository: TrackRepository,
    private val catalogItemRepository: CatalogItemRepository,
) {
    data class ChartResult(
        val chart: ChartEntity,
        val catalogItem: CatalogItemEntity,
        val track: TrackEntity,
        val contributors: List<Pair<ContributorEntity, UserEntity>>,
        val streamingRefs: List<StreamingRef>,
        val latestVersion: VersionEntity?,
        val userStats: Pair<LocalDateTime?, LocalDateTime?>,
    )

    fun toChart(result: ChartResult): Chart = Chart(
        id = result.chart.id.value.toString(),
        contentId = result.catalogItem.id.value,
        status = result.catalogItem.status,
        isPublic = result.catalogItem.isPublic,
        isFeatured = result.catalogItem.isFeatured,
        downloadsSum = result.catalogItem.downloadsSum,
        contributors = result.contributors.map { contributorEntityToContributor(it.first, it.second) },
        createdAt = result.catalogItem.createdAt,
        publishedAt = result.catalogItem.publishedAt,
        updatedAt = result.catalogItem.updatedAt,
        likedAt = result.userStats.first,
        bookmarkedAt = result.userStats.second,
        previewVideoId = result.catalogItem.previewVideoId,
        track = trackRepository.toTrack(result.track, result.streamingRefs),
        versionsCount = result.catalogItem.versionsCount,
        difficulty = result.chart.difficulty,
        notesAmount = result.chart.notesAmount,
        effectsAmount = result.chart.effectsAmount,
        isDeluxe = result.chart.isDeluxe,
        isExplicit = result.chart.isExplicit,
        latestVersion = result.latestVersion?.let(VersionMapper::entityToVersion),
    )

    suspend fun processResultsInMemory(
        requestingUserId: UUID?,
        results: List<ResultRow>,
        includeStreamingRefs: Boolean,
    ): List<ChartResult> {
        val groupedByChartId = results.groupBy { it[ChartTable.id].value }
        val contentIds = groupedByChartId.values.map { it.first()[CatalogItemTable.id].value }
        val userStats = catalogItemRepository.fetchUserStats(requestingUserId, contentIds)

        return groupedByChartId.map { (_, rows) ->
            val chartEntity = ChartEntity.wrapRow(rows.first())
            val catalogItemEntity = CatalogItemEntity.wrapRow(rows.first())
            val trackEntity = TrackEntity.wrapRow(rows.first())

            val streamingRefs = if (includeStreamingRefs) {
                rows.mapNotNull { row ->
                    val platform = row.getOrNull(TrackStreamingRefTable.platform)
                    val externalId = row.getOrNull(TrackStreamingRefTable.externalId)
                    if (platform != null && externalId != null) {
                        StreamingRef(platform, StreamingPlatformUtils.buildUrl(platform, externalId))
                    } else null
                }.distinctBy { it.platform to it.url }
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
            )
        }
    }
}

