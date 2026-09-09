package org.bscm.repository

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.dto.overview.DailyDownload
import org.bscm.models.dto.overview.LatestUpdate
import org.bscm.models.dto.overview.TopContentItem
import org.bscm.models.dto.overview.TypeBreakdown
import org.bscm.models.enums.ActivityType
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.CollectionKind
import org.bscm.models.tables.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import java.util.*
import kotlin.time.Clock

class OverviewRepository {

    private fun rangeStartForDays(days: Int): LocalDateTime {
        val now = Clock.System.now()
        val ldt = now.toLocalDateTime(TimeZone.UTC)
        val adjusted = ldt.date.minus(days, kotlinx.datetime.DateTimeUnit.DAY)
        return LocalDateTime(
            year = adjusted.year,
            month = adjusted.month,
            day = adjusted.day,
            hour = ldt.hour,
            minute = ldt.minute,
            second = ldt.second,
            nanosecond = ldt.nanosecond,
        )
    }

    fun getPublishedCounts(userId: UUID): TypeBreakdown {
        val countColumn = CatalogItemTable.id.count()
        val rows = CatalogItemTable
            .select(CatalogItemTable.type, countColumn)
            .where {
                (CatalogItemTable.authorId eq userId) and
                (CatalogItemTable.status eq CatalogItemStatus.PUBLISHED)
            }
            .groupBy(CatalogItemTable.type)
            .associate { it[CatalogItemTable.type] to it[countColumn].toInt() }

        return TypeBreakdown(
            charts = rows[CatalogItemType.CHART] ?: 0,
            tourPasses = rows[CatalogItemType.TOUR_PASS] ?: 0,
            themes = rows[CatalogItemType.THEME] ?: 0,
        )
    }

    fun getPublishedTrend(userId: UUID): Int {
        val weekAgo = rangeStartForDays(7)

        return CatalogItemTable
            .select(CatalogItemTable.id)
            .where {
                (CatalogItemTable.authorId eq userId) and
                (CatalogItemTable.status eq CatalogItemStatus.PUBLISHED) and
                (CatalogItemTable.createdAt greaterEq weekAgo)
            }
            .count()
            .toInt()
    }

    fun getContributedCounts(userId: UUID): TypeBreakdown {
        val countColumn = CatalogItemTable.id.count()
        val rows = ContributorTable
            .innerJoin(CatalogItemTable, { ContributorTable.catalogItemId }, { CatalogItemTable.id })
            .select(CatalogItemTable.type, countColumn)
            .where {
                (ContributorTable.userId eq userId) and
                (CatalogItemTable.authorId neq userId)
            }
            .groupBy(CatalogItemTable.type)
            .associate { it[CatalogItemTable.type] to it[countColumn].toInt() }

        return TypeBreakdown(
            charts = rows[CatalogItemType.CHART] ?: 0,
            tourPasses = rows[CatalogItemType.TOUR_PASS] ?: 0,
            themes = rows[CatalogItemType.THEME] ?: 0,
        )
    }

    fun getVersionUpdateCounts(userId: UUID, rangeStart: LocalDateTime): TypeBreakdown {
        val countColumn = VersionTable.id.count()
        val rows = VersionTable
            .innerJoin(CatalogItemTable, { VersionTable.catalogItemId }, { CatalogItemTable.id })
            .select(CatalogItemTable.type, countColumn)
            .where {
                (CatalogItemTable.authorId eq userId) and
                (VersionTable.createdAt greaterEq rangeStart) and
                (VersionTable.versionCode greater 1)
            }
            .groupBy(CatalogItemTable.type)
            .associate { it[CatalogItemTable.type] to it[countColumn].toInt() }

        return TypeBreakdown(
            charts = rows[CatalogItemType.CHART] ?: 0,
            tourPasses = rows[CatalogItemType.TOUR_PASS] ?: 0,
            themes = rows[CatalogItemType.THEME] ?: 0,
        )
    }

    fun getLatestVersionUpdate(userId: UUID): LatestUpdate? {
        val row = VersionTable
            .innerJoin(CatalogItemTable, { VersionTable.catalogItemId }, { CatalogItemTable.id })
            .leftJoin(ChartTable, { CatalogItemTable.id }, { ChartTable.id })
            .leftJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
            .leftJoin(TourPassTable, { CatalogItemTable.id }, { TourPassTable.id })
            .leftJoin(ThemeTable, { CatalogItemTable.id }, { ThemeTable.id })
            .select(
                CatalogItemTable.id,
                CatalogItemTable.type,
                TrackTable.title,
                TourPassTable.name,
                ThemeTable.name,
                VersionTable.versionCode,
                VersionTable.createdAt,
            )
            .where {
                (CatalogItemTable.authorId eq userId) and
                (VersionTable.versionCode greater 1)
            }
            .orderBy(VersionTable.createdAt to SortOrder.DESC)
            .limit(1)
            .firstOrNull() ?: return null

        val type = row[CatalogItemTable.type]
        val name = when (type) {
            CatalogItemType.CHART -> row[TrackTable.title]
            CatalogItemType.TOUR_PASS -> row[TourPassTable.name]
            CatalogItemType.THEME -> row[ThemeTable.name]
        }

        return LatestUpdate(
            catalogItemId = row[CatalogItemTable.id].value,
            name = name,
            type = type,
            versionCode = row[VersionTable.versionCode],
            publishedAt = row[VersionTable.createdAt],
        )
    }

    fun getDownloadTotals(userId: UUID): Int {
        val sumColumn = CatalogItemTable.downloadsSum.sum()
        return CatalogItemTable
            .select(sumColumn)
            .where { CatalogItemTable.authorId eq userId }
            .first()[sumColumn] ?: 0
    }

    fun getDownloadTrendPercent(userId: UUID): Double? {
        val thirtyDaysAgo = rangeStartForDays(30)
        val sixtyDaysAgo = rangeStartForDays(60)

        val recentCount = DownloadEventTable
            .innerJoin(CatalogItemTable, { DownloadEventTable.catalogItemId }, { CatalogItemTable.id })
            .select(DownloadEventTable.id.count())
            .where {
                (CatalogItemTable.authorId eq userId) and
                (DownloadEventTable.createdAt greaterEq thirtyDaysAgo)
            }
            .count()

        val previousCount = DownloadEventTable
            .innerJoin(CatalogItemTable, { DownloadEventTable.catalogItemId }, { CatalogItemTable.id })
            .select(DownloadEventTable.id.count())
            .where {
                (CatalogItemTable.authorId eq userId) and
                (DownloadEventTable.createdAt greaterEq sixtyDaysAgo) and
                (DownloadEventTable.createdAt less thirtyDaysAgo)
            }
            .count()

        if (previousCount == 0L) return null
        val percent = ((recentCount - previousCount).toDouble() / previousCount.toDouble()) * 100.0
        return kotlin.math.round(percent * 10.0) / 10.0
    }

    fun getDownloadBreakdownByType(userId: UUID): TypeBreakdown {
        val sumColumn = CatalogItemTable.downloadsSum.sum()
        val rows = CatalogItemTable
            .select(CatalogItemTable.type, sumColumn)
            .where { CatalogItemTable.authorId eq userId }
            .groupBy(CatalogItemTable.type)
            .associate { it[CatalogItemTable.type] to (it[sumColumn] ?: 0) }

        return TypeBreakdown(
            charts = rows[CatalogItemType.CHART] ?: 0,
            tourPasses = rows[CatalogItemType.TOUR_PASS] ?: 0,
            themes = rows[CatalogItemType.THEME] ?: 0,
        )
    }

    fun getDailyDownloads(userId: UUID, days: Int): List<DailyDownload> {
        val rangeStart = rangeStartForDays(days)

        val typeColumn = CatalogItemTable.type
        val countColumn = DownloadEventTable.id.count()

        val rows = DownloadEventTable
            .innerJoin(CatalogItemTable, { DownloadEventTable.catalogItemId }, { CatalogItemTable.id })
            .select(typeColumn, countColumn)
            .where {
                (CatalogItemTable.authorId eq userId) and
                (DownloadEventTable.createdAt greaterEq rangeStart)
            }
            .groupBy(typeColumn)
            .toList()

        val byType = rows.associate { it[typeColumn] to it[countColumn].toInt() }

        return listOf(
            DailyDownload(
                date = "total",
                charts = byType[CatalogItemType.CHART] ?: 0,
                tourPasses = byType[CatalogItemType.TOUR_PASS] ?: 0,
                themes = byType[CatalogItemType.THEME] ?: 0,
            )
        )
    }

    fun getRecentActivityFeedItems(userId: UUID, limit: Int): List<Triple<String, String, LocalDateTime>> {
        return UserActivityTable
            .select(UserActivityTable.type, UserActivityTable.targetId, UserActivityTable.createdAt)
            .where { UserActivityTable.userId eq userId }
            .orderBy(UserActivityTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                Triple(
                    row[UserActivityTable.type].name,
                    row[UserActivityTable.targetId],
                    row[UserActivityTable.createdAt]
                )
            }
    }

    fun getTotalLikes(userId: UUID): Int {
        val collection = CollectionTable
            .select(CollectionTable.id)
            .where {
                (CollectionTable.userId eq userId) and
                (CollectionTable.kind eq CollectionKind.LIKES)
            }
            .singleOrNull() ?: return 0

        return CollectionItemTable
            .select(CollectionItemTable.id)
            .where { CollectionItemTable.collectionId eq collection[CollectionTable.id] }
            .count()
            .toInt()
    }

    fun getTotalBookmarks(userId: UUID): Int {
        val collection = CollectionTable
            .select(CollectionTable.id)
            .where {
                (CollectionTable.userId eq userId) and
                (CollectionTable.kind eq CollectionKind.BOOKMARKS)
            }
            .singleOrNull() ?: return 0

        return CollectionItemTable
            .select(CollectionItemTable.id)
            .where { CollectionItemTable.collectionId eq collection[CollectionTable.id] }
            .count()
            .toInt()
    }

    fun getTopContent(userId: UUID, type: CatalogItemType, limit: Int): List<TopContentItem> {
        return when (type) {
            CatalogItemType.CHART -> {
                ChartTable
                    .innerJoin(CatalogItemTable, { ChartTable.id }, { CatalogItemTable.id })
                    .innerJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
                    .select(CatalogItemTable.id, TrackTable.title, CatalogItemTable.downloadsSum)
                    .where { CatalogItemTable.authorId eq userId }
                    .orderBy(CatalogItemTable.downloadsSum to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        TopContentItem(
                            catalogItemId = row[CatalogItemTable.id].value,
                            name = row[TrackTable.title],
                            type = CatalogItemType.CHART,
                            downloads = row[CatalogItemTable.downloadsSum],
                        )
                    }
            }

            CatalogItemType.TOUR_PASS -> {
                TourPassTable
                    .innerJoin(CatalogItemTable, { TourPassTable.id }, { CatalogItemTable.id })
                    .select(CatalogItemTable.id, TourPassTable.name, CatalogItemTable.downloadsSum)
                    .where { CatalogItemTable.authorId eq userId }
                    .orderBy(CatalogItemTable.downloadsSum to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        TopContentItem(
                            catalogItemId = row[CatalogItemTable.id].value,
                            name = row[TourPassTable.name],
                            type = CatalogItemType.TOUR_PASS,
                            downloads = row[CatalogItemTable.downloadsSum],
                        )
                    }
            }

            CatalogItemType.THEME -> {
                ThemeTable
                    .innerJoin(CatalogItemTable, { ThemeTable.id }, { CatalogItemTable.id })
                    .select(CatalogItemTable.id, ThemeTable.name, CatalogItemTable.downloadsSum)
                    .where { CatalogItemTable.authorId eq userId }
                    .orderBy(CatalogItemTable.downloadsSum to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        TopContentItem(
                            catalogItemId = row[CatalogItemTable.id].value,
                            name = row[ThemeTable.name],
                            type = CatalogItemType.THEME,
                            downloads = row[CatalogItemTable.downloadsSum],
                        )
                    }
            }
        }
    }

    fun resolveActivityContentNames(
        targetIds: Map<String, ActivityType>
    ): Map<String, Pair<String, CatalogItemType>> {
        if (targetIds.isEmpty()) return emptyMap()

        val chartIds = mutableListOf<String>()
        val tourPassIds = mutableListOf<String>()
        val themeIds = mutableListOf<String>()

        targetIds.forEach { (targetId, type) ->
            when (type) {
                ActivityType.CREATED_CHART,
                ActivityType.LIKED_CHART,
                ActivityType.BOOKMARKED_CHART -> chartIds.add(targetId)
                ActivityType.CREATED_TOUR_PASS,
                ActivityType.LIKED_TOUR_PASS,
                ActivityType.BOOKMARKED_TOUR_PASS -> tourPassIds.add(targetId)
                ActivityType.CREATED_THEME,
                ActivityType.LIKED_THEME,
                ActivityType.BOOKMARKED_THEME -> themeIds.add(targetId)
                ActivityType.FOLLOWED_USER -> {}
            }
        }

        val result = mutableMapOf<String, Pair<String, CatalogItemType>>()

        if (chartIds.isNotEmpty()) {
            ChartTable
                .innerJoin(CatalogItemTable, { ChartTable.id }, { CatalogItemTable.id })
                .innerJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
                .select(CatalogItemTable.id, TrackTable.title)
                .where { CatalogItemTable.id inList chartIds }
                .forEach { row ->
                    result[row[CatalogItemTable.id].value] = Pair(
                        row[TrackTable.title],
                        CatalogItemType.CHART
                    )
                }
        }

        if (tourPassIds.isNotEmpty()) {
            TourPassTable
                .innerJoin(CatalogItemTable, { TourPassTable.id }, { CatalogItemTable.id })
                .select(CatalogItemTable.id, TourPassTable.name)
                .where { CatalogItemTable.id inList tourPassIds }
                .forEach { row ->
                    result[row[CatalogItemTable.id].value] = Pair(
                        row[TourPassTable.name],
                        CatalogItemType.TOUR_PASS
                    )
                }
        }

        if (themeIds.isNotEmpty()) {
            ThemeTable
                .innerJoin(CatalogItemTable, { ThemeTable.id }, { CatalogItemTable.id })
                .select(CatalogItemTable.id, ThemeTable.name)
                .where { CatalogItemTable.id inList themeIds }
                .forEach { row ->
                    result[row[CatalogItemTable.id].value] = Pair(
                        row[ThemeTable.name],
                        CatalogItemType.THEME
                    )
                }
        }

        return result
    }
}
