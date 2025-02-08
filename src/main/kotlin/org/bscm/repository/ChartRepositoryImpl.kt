package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.*
import org.bscm.models.dto.CreateChartRequest
import org.bscm.models.dto.UpdateChartRequest
import org.bscm.models.entities.ChartEntity
import org.bscm.models.entities.ContributorEntity
import org.bscm.models.entities.UserEntity
import org.bscm.models.entities.VersionEntity
import org.bscm.models.enums.ContributorRole
import org.bscm.models.tables.ContributorTable
import org.jetbrains.exposed.dao.id.CompositeID
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
        coverUrl = entity.coverUrl,
        isDeluxe = entity.isDeluxe,
        isExplicit = entity.isExplicit,
        difficulty = entity.difficulty,
        isFeatured = entity.isFeatured,
        versions = versionEntities?.map(::versionEntityToVersion) ?: emptyList(),
        contributors = contributorEntities?.map(::contributorEntityToContributor) ?: emptyList(),
    )

    private fun versionEntityToVersion(entity: VersionEntity): Version = Version(
        id = entity.id.value,
        chartId = entity.chart.id.value,
        index = entity.index,
        chartUrl = entity.chartUrl,
        duration = entity.duration,
        notesAmount = entity.notesAmount,
        effectsAmount = entity.effectsAmount,
        bpm = entity.bpm,
        downloadsAmount = entity.downloadsAmount,
        knownIssues = entity.knownIssues,
        publishedAt = entity.publishedAt,
    )

    private fun userEntityToUser(entity: UserEntity): User = User(
        id = entity.id.value,
        username = entity.username,
        email = entity.email,
        imageUrl = entity.imageUrl,
        discordId = entity.discordId,
        createdAt = entity.createdAt,
    )

    private fun contributorEntityToContributor(entity: ContributorEntity): Contributor {
        val compositeId = entity.id.value // This is a CompositeID
        val chartId = compositeId[ContributorTable.chartId].value

        return Contributor(
            user = userEntityToUser(entity.user),
            chartId = chartId,
            roles = entity.roles,
            joinedAt = entity.joinedAt,
        )
    }

    override suspend fun getAllCharts(
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): List<Chart> = newSuspendedTransaction {
        // Retrieve charts whose latest version's createdAt is within the given range
        ChartEntity.all()
            .mapNotNull { chartEntity ->
                // Get the latest version for this chart
                val latestVersion = chartEntity.versions
                    .toList()
                    .maxByOrNull { it.index }

                // Filter based on the latest version's createdAt timestamp
                if (latestVersion != null &&
                    (startDate == null || latestVersion.publishedAt >= startDate) &&
                    (endDate == null || latestVersion.publishedAt <= endDate)
                ) {
                    chartEntityToChart(chartEntity, listOf(latestVersion), chartEntity.contributors.toList())
                } else {
                    null // Exclude charts if no version matches the range
                }
            }
    }

    override suspend fun getChartById(id: UUID): Chart? = newSuspendedTransaction {
        ChartEntity.findById(id)?.let { chartEntity ->
            chartEntityToChart(chartEntity, chartEntity.versions.toList(), chartEntity.contributors.toList())
        }

        /*
        * // Select chart data first
        val chartEntity = ChartEntity.findById(id) ?: return@newSuspendedTransaction null

        // Perform a manual query to select only the needed fields (username, imageUrl) for contributors
        val contributors = UserTable
            .join(ContributorTable, JoinType.INNER, additionalConstraint = { ContributorTable.chartId eq id })
            .select(UserTable.username, UserTable.imageUrl)
            .map {
                SimplifiedUser().apply {
                    username = it[UserTable.username]
                    imageUrl = it[UserTable.imageUrl]
                }
            }

        // Map to Chart DTO, including the contributors
        chartEntityToChart(chartEntity, chartEntity.versions.toList()).copy(
            contributors = contributors
        )*/
    }

    override suspend fun createChart(chart: CreateChartRequest): Chart = newSuspendedTransaction {
        // Create the chart
        val newChart = ChartEntity.new(UUID.randomUUID()) {
            this.artist = chart.artist
            this.track = chart.track
            this.album = chart.album
            this.coverUrl = chart.coverUrl
            this.difficulty = chart.difficulty
            this.isDeluxe = chart.isDeluxe
            this.isExplicit = chart.isExplicit
        }

        // Add the initial version with the chart's metadata
        val initialVersion = VersionEntity.new {
            this.chart = newChart
            this.chartUrl = chart.chartUrl
            this.duration = chart.duration
            this.notesAmount = chart.notesAmount
            this.effectsAmount = chart.effectsAmount
            this.bpm = chart.bpm
        }

        val user = UserEntity.findById(UUID.fromString(("547a6044-fe49-4ff5-b7cc-dc2b4e6651a5")))
            ?: throw NotFoundException("User not found")

        val contributorId = CompositeID {
            it[ContributorTable.chartId] = newChart.id
            it[ContributorTable.userId] = user.id
        }

        // Add the user as an author of the chart
        ContributorEntity.new(contributorId) {
            roles = listOf(ContributorRole.Author)
            joinedAt = LocalDate.now()
        }

        chartEntityToChart(newChart, listOf(initialVersion))
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
        }
        chartEntityToChart(existingChart)
    }

    override suspend fun deleteChart(id: UUID): Boolean = newSuspendedTransaction {
        val chart = ChartEntity.findById(id) ?: return@newSuspendedTransaction false
        chart.delete()
        true
    }

    override suspend fun addIssue(chartId: UUID, issue: KnownIssue): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun removeIssue(chartId: UUID, issueId: UUID): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun addContributor(chartId: UUID, userId: UUID): Boolean = newSuspendedTransaction {
        val chart = ChartEntity.findById(chartId) ?: return@newSuspendedTransaction false
        println("Chart: $chart")
        val user = UserEntity.findById(userId) ?: return@newSuspendedTransaction false
        println("User: $user")

        val contributorId = CompositeID {
            it[ContributorTable.chartId] = chart.id
            it[ContributorTable.userId] = user.id
        }

        ContributorEntity.new(contributorId) {
            roles = listOf(ContributorRole.Author)
            joinedAt = LocalDate.now()
        }

        true
    }

    override suspend fun removeContributor(chartId: UUID, userId: UUID): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getContributors(chartId: UUID): List<UUID> {
        TODO("Not yet implemented")
    }

    override suspend fun addVersion(chartId: UUID): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun removeVersion(chartId: UUID, versionId: UUID): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getVersions(chartId: UUID): List<UUID> {
        TODO("Not yet implemented")
    }
}