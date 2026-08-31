package org.bscm.models.dto.overview

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bscm.models.enums.CatalogItemType

@Serializable
data class OverviewResponse(
    val published: OverviewStat,
    val contributed: OverviewStat,
    val updates: OverviewUpdates,
    val downloads: OverviewDownloads,
    val activityFeed: List<OverviewFeedItem>,
    val feedback: OverviewFeedback,
)

@Serializable
data class OverviewStat(
    val total: Int,
    val byType: TypeBreakdown,
    val trend: TrendIndicator? = null,
)

@Serializable
data class OverviewUpdates(
    val total: Int,
    val byType: TypeBreakdown,
    val latestUpdate: LatestUpdate? = null,
)

@Serializable
data class OverviewDownloads(
    val total: Int,
    val trendPercent: Double? = null,
    val byType: TypeBreakdown,
    val dailyBreakdown: List<DailyDownload> = emptyList(),
)

@Serializable
data class TypeBreakdown(
    val charts: Int = 0,
    val tourPasses: Int = 0,
    val themes: Int = 0,
)

@Serializable
data class TrendIndicator(
    val value: String,
)

@Serializable
data class LatestUpdate(
    val catalogItemId: String,
    val name: String,
    val type: CatalogItemType,
    val versionCode: Int,
    val publishedAt: LocalDateTime,
)

@Serializable
data class DailyDownload(
    val date: String,
    val charts: Int = 0,
    val tourPasses: Int = 0,
    val themes: Int = 0,
)

@Serializable
data class OverviewFeedItem(
    val id: String,
    val type: String,
    val catalogItemId: String,
    val contentType: CatalogItemType,
    val contentName: String,
    val createdAt: LocalDateTime,
)

@Serializable
data class OverviewFeedback(
    val totalLikes: Int,
    val totalBookmarks: Int,
    val topContent: List<TopContentItem>,
)

@Serializable
data class TopContentItem(
    val catalogItemId: String,
    val name: String,
    val type: CatalogItemType,
    val downloads: Int,
)
