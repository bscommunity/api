package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.Chart
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.chart.UpdateChartRequest
import org.bscm.models.entities.ChartEntity
import org.bscm.models.entities.ContributorEntity
import org.bscm.models.entities.UserEntity
import org.bscm.models.entities.VersionEntity
import org.bscm.models.enums.ChartSortOption
import org.bscm.models.enums.ContributorRole
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContributorTable
import org.bscm.models.tables.VersionTable
import org.bscm.repository.ChartRepository
import org.bscm.repository.implementation.ContributorRepositoryImpl.Companion.contributorEntityToContributor
import org.bscm.repository.implementation.VersionRepositoryImpl.Companion.versionEntityToVersion
import org.jetbrains.exposed.dao.flushCache
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.util.*

class ChartRepositoryImpl : ChartRepository {

    private fun chartEntityToChart(
        entity: ChartEntity,
        versionEntities: List<VersionEntity>? = null,
        contributorEntities: List<ContributorEntity>? = null
    ): Chart = Chart(
        id = entity.id.value,
        track = entity.track,
        artist = entity.artist,
        album = entity.album,
        trackUrls = entity.trackUrls,
        trackPreviewUrl = entity.trackPreviewUrl,
        coverUrl = entity.coverUrl,
        isDeluxe = entity.isDeluxe,
        isExplicit = entity.isExplicit,
        difficulty = entity.difficulty,
        isFeatured = entity.isFeatured,
        genre = entity.genre,
        latestVersion = entity.latestVersion?.let(::versionEntityToVersion),
        versions = versionEntities?.map(::versionEntityToVersion) ?: emptyList(),
        contributors = contributorEntities?.map(::contributorEntityToContributor) ?: emptyList(),
    )

    override suspend fun getCharts(
        chartIds: List<UUID>?,
        query: String?,
        sortBy: ChartSortOption,
        difficulties: List<Difficulty>?,
        genres: List<Genre>?,
        limit: Int?,
        offset: Int?,
        fetchVersions: Boolean,
        fetchContributors: Boolean,
    ): List<Chart> = newSuspendedTransaction {
        val startTime = System.currentTimeMillis()

        // Build the initial condition as always true
        var conditions: Op<Boolean> = Op.TRUE

        // Add chartIds filter if provided
        if (!chartIds.isNullOrEmpty()) {
            conditions = conditions and (ChartTable.id inList chartIds)
        }

        // Add search query filter if provided
        if (!query.isNullOrBlank()) {
            val searchTerm = "%${query.lowercase()}%"
            conditions = conditions and (
                    (ChartTable.artist.lowerCase() like searchTerm) or
                            (ChartTable.track.lowerCase() like searchTerm) or
                            (ChartTable.album.lowerCase() like searchTerm)
                    )
        }

        // Add difficulty filter if specified
        if (!difficulties.isNullOrEmpty()) {
            conditions = conditions and (ChartTable.difficulty inList difficulties)
        }

        // Add genres filter if specified
        if (!genres.isNullOrEmpty()) {
            conditions = conditions and (ChartTable.genre inList genres)
        }

        // Determine the effective sort option
        val effectiveSortOption = when (sortBy) {
            ChartSortOption.WEEKLY_RANK, ChartSortOption.MOST_LIKED -> ChartSortOption.LAST_UPDATED
            else -> sortBy
        }

        // Apply sorting based on the effective sort option
        val queryBuilder = ChartEntity.find { conditions }

        // Eagerly load latest versions to avoid N+1 queries
        val query = when (effectiveSortOption) {
            ChartSortOption.LAST_UPDATED -> queryBuilder.with(ChartEntity::latestVersion).orderBy(
                VersionTable.publishedAt to SortOrder.DESC_NULLS_LAST
            )
            ChartSortOption.MOST_DOWNLOADED -> {
                // This sort requires more complex handling
                // We'll use a subquery to calculate the total downloads
                val chartDownloads = VersionTable
                    .slice(VersionTable.chartId, VersionTable.downloadsAmount.sum().alias("total_downloads"))
                    .select { VersionTable.chartId inList queryBuilder.map { it.id.value } }
                    .groupBy(VersionTable.chartId)
                    .alias("chart_downloads")

                queryBuilder.with(ChartEntity::latestVersion)
                    .leftJoin(chartDownloads, { ChartTable.id }, { chartDownloads[VersionTable.chartId] })
                    .orderBy(ExpressionAlias(chartDownloads["total_downloads"] ?: intLiteral(0), false) to SortOrder.DESC_NULLS_LAST)
            }
            else -> queryBuilder.with(ChartEntity::latestVersion)
        }

        // Apply pagination and execute query
        val paginatedCharts = query
            .limit(limit ?: Int.MAX_VALUE, offset?.toLong() ?: 0)
            .toList()

        // Early return for empty results
        if (paginatedCharts.isEmpty()) {
            val endTime = System.currentTimeMillis()
            println("Chart query completed in ${endTime - startTime}ms with 0 results")
            return@newSuspendedTransaction emptyList()
        }

        // Get all chart IDs for related data fetching
        val chartIdValues = paginatedCharts.map { it.id.value }

        // Batch load all versions if needed (in a single query)
        val versionsMap = if (fetchVersions) {
            VersionEntity.find { VersionTable.chartId inList chartIdValues }
                .with(VersionEntity::chart)  // Eagerly load chart relationship
                .toList()
                .groupBy { it.chart.id.value }
        } else {
            emptyMap()
        }

        // Batch load all contributors if needed (in a single query)
        val contributorsMap = if (fetchContributors) {
            ContributorEntity.find { ContributorTable.chartId inList chartIdValues }
                .toList()
                .groupBy { it.id.value[ContributorTable.chartId].value }
        } else {
            emptyMap()
        }

        // Map charts to domain models
        val result = paginatedCharts.map { entity ->
            chartEntityToChart(
                entity,
                versionEntities = versionsMap[entity.id.value],
                contributorEntities = contributorsMap[entity.id.value]
            )
        }

        val endTime = System.currentTimeMillis()
        println("Chart query completed in ${endTime - startTime}ms with ${result.size} results")

        result
    }


    override suspend fun getChartById(id: UUID): Chart? = newSuspendedTransaction {
        ChartEntity.findById(id)?.let { chartEntity ->
            chartEntityToChart(chartEntity, chartEntity.versions.toList(), chartEntity.contributors.toList())
        }
    }

    override suspend fun getSuggestions(query: String, limit: Int): List<String> = newSuspendedTransaction {
        val startTime = System.currentTimeMillis()

        val searchTerm = "%${query.lowercase()}%"

        val result = ChartTable
            .select(
                listOf(
                    ChartTable.artist,
                    ChartTable.track,
                    ChartTable.album
                )
            )
            .where {
                (ChartTable.artist.lowerCase() like searchTerm) or
                        (ChartTable.track.lowerCase() like searchTerm) or
                        (ChartTable.album.lowerCase() like searchTerm)
            }
            .limit(limit)
            .map { it ->
                listOf(it[ChartTable.artist], it[ChartTable.track], it[ChartTable.album]).firstOrNull {
                    it?.lowercase()?.contains(query.lowercase()) ?: false
                } ?: it[ChartTable.track]
            }
            .distinct()

        val endTime = System.currentTimeMillis()
        println("Chart query completed in ${endTime - startTime}ms with ${result.size} results")

        result
    }

    override suspend fun createChart(userId: UUID, chart: CreateChartRequest): Chart = newSuspendedTransaction {
        // Create the chart
        val newChart = ChartEntity.new {
            this.artist = chart.artist
            this.track = chart.track
            this.album = chart.album
            this.trackUrls = chart.trackUrls
            this.trackPreviewUrl = chart.trackPreviewUrl
            this.coverUrl = chart.coverUrl
            this.difficulty = chart.difficulty
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
            this.genre = chart.genre
            this.latestVersion = null
        }

        flushCache()

        // Add the initial version with the chart's metadata
        val initialVersion = VersionEntity.new {
            this.chart = newChart
            this.index = 0
            this.chartUrl = chart.chartUrl
            this.chartPreviewUrls = chart.chartPreviewUrls
            this.duration = chart.duration
            this.notesAmount = chart.notesAmount
            this.effectsAmount = chart.effectsAmount
            this.bpm = chart.bpm
        }

        ChartEntity.findByIdAndUpdate(newChart.id.value) {
            it.latestVersion = initialVersion
        }

        val user = UserEntity.findById(userId) ?: throw NotFoundException("User not found")

        val contributorId = CompositeID {
            it[ContributorTable.chartId] = newChart.id
            it[ContributorTable.userId] = user.id
        }

        // Add the user as an author of the chart
        ContributorEntity.new(contributorId) {
            roles = listOf(ContributorRole.AUTHOR)
            joinedAt = LocalDate.now()
        }

        // Since the new chart will be cached on the creator device,
        // we need to return all the data to avoid inconsistencies
        chartEntityToChart(newChart, newChart.versions.toList(), newChart.contributors.toList())
    }

    override suspend fun updateChart(id: UUID, chart: UpdateChartRequest): Chart = newSuspendedTransaction {
        val existingChart = ChartEntity.findById(id) ?: throw NotFoundException("Chart not found")
        existingChart.apply {
            artist = chart.artist ?: artist
            track = chart.track ?: track
            coverUrl = chart.coverUrl ?: coverUrl
            difficulty = chart.difficulty ?: difficulty
            isDeluxe = chart.isDeluxe ?: isDeluxe
            isExplicit = chart.isExplicit ?: isExplicit
            isFeatured = chart.isFeatured ?: isFeatured
            genre = chart.genre ?: genre
        }
        chartEntityToChart(existingChart)
    }

    override suspend fun deleteChart(id: UUID): Boolean = newSuspendedTransaction {
        val chart = ChartEntity.findById(id) ?: return@newSuspendedTransaction false
        chart.delete()
        true
    }
}