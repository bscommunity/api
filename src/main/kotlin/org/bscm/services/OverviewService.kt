package org.bscm.services

import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.bscm.models.dto.overview.*
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.CatalogItemType
import org.bscm.repository.OverviewRepository
import org.bscm.utils.withRetryOnTransientErrors
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.Clock

class OverviewService(
    private val overviewRepository: OverviewRepository,
) {

    suspend fun getOverview(userId: UUID, rangeParam: String): OverviewResponse = withRetryOnTransientErrors {
        val days = parseRange(rangeParam)
        val rangeStart = Clock.System.now().toLocalDateTime(TimeZone.UTC).let { ldt ->
            val adjusted = ldt.date.minus(days, DateTimeUnit.DAY)
            LocalDateTime(
                year = adjusted.year,
                month = adjusted.month,
                day = adjusted.day,
                hour = ldt.hour,
                minute = ldt.minute,
                second = ldt.second,
                nanosecond = ldt.nanosecond,
            )
        }

        suspendTransaction {
            val publishedCounts = overviewRepository.getPublishedCounts(userId)
            val publishedTrend = overviewRepository.getPublishedTrend(userId)
            val contributedCounts = overviewRepository.getContributedCounts(userId)
            val updateCounts = overviewRepository.getVersionUpdateCounts(userId, rangeStart)
            val latestUpdate = overviewRepository.getLatestVersionUpdate(userId)
            val downloadTotal = overviewRepository.getDownloadTotals(userId)
            val downloadTrend = overviewRepository.getDownloadTrendPercent(userId)
            val downloadBreakdown = overviewRepository.getDownloadBreakdownByType(userId)
            val totalLikes = overviewRepository.getTotalLikes(userId)
            val totalBookmarks = overviewRepository.getTotalBookmarks(userId)
            val topCharts = overviewRepository.getTopContent(userId, CatalogItemType.CHART, 1)
            val topTourPasses = overviewRepository.getTopContent(userId, CatalogItemType.TOUR_PASS, 1)
            val topThemes = overviewRepository.getTopContent(userId, CatalogItemType.THEME, 1)

            val rawActivity = overviewRepository.getRecentActivityFeedItems(userId, 10)
            val targetTypeMap = rawActivity.associate { (type, targetId, _) ->
                targetId to ActivityType.valueOf(type)
            }
            val resolvedNames = overviewRepository.resolveActivityContentNames(targetTypeMap)

            val activityFeed = rawActivity.mapNotNull { (type, targetId, createdAt) ->
                val (contentName, contentType) = resolvedNames[targetId] ?: return@mapNotNull null

                OverviewFeedItem(
                    id = targetId,
                    type = type.lowercase(),
                    catalogItemId = targetId,
                    contentType = contentType,
                    contentName = contentName,
                    createdAt = createdAt,
                )
            }

            OverviewResponse(
                published = OverviewStat(
                    total = publishedCounts.charts + publishedCounts.tourPasses + publishedCounts.themes,
                    byType = publishedCounts,
                    trend = TrendIndicator("+$publishedTrend this week"),
                ),
                contributed = OverviewStat(
                    total = contributedCounts.charts + contributedCounts.tourPasses + contributedCounts.themes,
                    byType = contributedCounts,
                ),
                updates = OverviewUpdates(
                    total = updateCounts.charts + updateCounts.tourPasses + updateCounts.themes,
                    byType = updateCounts,
                    latestUpdate = latestUpdate,
                ),
                downloads = OverviewDownloads(
                    total = downloadTotal,
                    trendPercent = downloadTrend,
                    byType = downloadBreakdown,
                ),
                activityFeed = activityFeed,
                feedback = OverviewFeedback(
                    totalLikes = totalLikes,
                    totalBookmarks = totalBookmarks,
                    topContent = topCharts + topTourPasses + topThemes,
                ),
            )
        }
    }

    private fun parseRange(range: String): Int = when (range) {
        "7d" -> 7
        "30d", "all" -> 30
        else -> 30
    }
}
