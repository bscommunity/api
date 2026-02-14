package org.bscm.repository

import org.bscm.models.Changelog
import org.bscm.models.Version
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.models.mappers.VersionMapper.rowToVersion
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class VersionRepository : IVersionRepository {

    // -------------------------------------------------------------------------
    // Index calculation
    // -------------------------------------------------------------------------

    /**
     * Computes 1-based version indices for every version belonging to the given
     * charts in a single round-trip using a ROW_NUMBER() window function.
     *
     * Analogy: think of this like Kotlin's mapIndexed, but done entirely inside
     * the database — the DB groups rows by chart_id (PARTITION BY) and numbers
     * them chronologically (ORDER BY created_at), so we never load extra rows.
     *
     * @return Map of versionId (ULong) → 1-based index
     */
    private suspend fun calculateVersionIndices(chartIds: List<ULong>): Map<ULong, Int> = newSuspendedTransaction {
        if (chartIds.isEmpty()) return@newSuspendedTransaction emptyMap()

        val inClause = chartIds.joinToString { it.toString() }
        val sql = """
            SELECT
                id,
                ROW_NUMBER() OVER (
                    PARTITION BY chart_id
                    ORDER BY created_at ASC
                ) AS version_index
            FROM ${VersionTable.tableName}
            WHERE chart_id IN ($inClause)
        """.trimIndent()

        val result = mutableMapOf<ULong, Int>()
        exec(sql) { rs ->
            while (rs.next()) {
                result[rs.getLong("id").toULong()] = rs.getInt("version_index")
            }
        }
        result
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns a single version by ID.
     *
     * The index is computed with a lightweight COUNT query scoped to versions
     * created at or before this one — correct and isolated to one row.
     */
    override suspend fun getVersionById(id: ULong): Version? = newSuspendedTransaction {
        val row = VersionTable
            .selectAll()
            .where { VersionTable.id eq id }
            .singleOrNull() ?: return@newSuspendedTransaction null

        val chartId = row[VersionTable.chartId].value
        val createdAt = row[VersionTable.createdAt]

        // COUNT(*) of versions created on or before this one = this version's 1-based index
        val index = VersionTable
            .select(VersionTable.id.count())
            .where {
                (VersionTable.chartId eq chartId) and
                        (VersionTable.createdAt lessEq createdAt)
            }
            .single()[VersionTable.id.count()]
            .toInt()

        rowToVersion(row, index)
    }

    /**
     * Returns all versions for a chart, each with a correct 1-based index.
     *
     * Uses calculateVersionIndices() so index computation is a single query
     * regardless of how many versions the chart has.
     */
    override suspend fun getVersions(chartId: ULong): List<Version> = newSuspendedTransaction {
        val rows = VersionTable
            .selectAll()
            .where { VersionTable.chartId eq chartId }
            .orderBy(VersionTable.createdAt to SortOrder.ASC)
            .toList()

        if (rows.isEmpty()) return@newSuspendedTransaction emptyList()

        val indices = calculateVersionIndices(listOf(chartId))

        rows.map { row -> rowToVersion(row, indices[row[VersionTable.id].value] ?: 1) }
    }

    /**
     * Returns the latest version for each chart in [chartIds] in a single JOIN.
     *
     * This follows the latestVersionId pointer on ChartTable directly — O(1)
     * per chart, no sorting, no in-memory deduplication.
     *
     * Bug fix: the previous implementation loaded ALL versions for all charts
     * and called distinctBy() in memory, defeating the purpose of latestVersionId.
     */
    override suspend fun getLatestVersionsByChartIds(chartIds: List<ULong>): List<Version> =
        newSuspendedTransaction {
            if (chartIds.isEmpty()) return@newSuspendedTransaction emptyList()

            // JOIN ChartTable on its latestVersionId pointer → one row per chart, no sorting
            val rows = (ChartTable innerJoin VersionTable)
                .select(VersionTable.columns)
                .where {
                    (ChartTable.id inList chartIds) and
                            (ChartTable.latestVersionId eq VersionTable.id)
                }
                .toList()

            if (rows.isEmpty()) return@newSuspendedTransaction emptyList()

            val indices = calculateVersionIndices(chartIds)

            rows.map { row -> rowToVersion(row, indices[row[VersionTable.id].value] ?: 1) }
        }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Inserts a new version and atomically updates ChartTable.latestVersionId.
     *
     * Both operations share a single transaction — if the chart update fails,
     * the insert is rolled back, keeping latestVersionId consistent.
     *
     * Bug fix: index is derived from the current version count before insertion,
     * not re-queried after, avoiding a redundant COUNT round-trip.
     *
     * Bug fix: changelog UUIDs are now assigned here consistently, regardless
     * of which overload is called.
     */
    override suspend fun addVersion(chartId: ULong, version: CreateVersionRequest): Version =
        newSuspendedTransaction {
            // Count existing versions to derive the new index without an extra query after insert
            val existingCount = VersionTable
                .select(VersionTable.id.count())
                .where { VersionTable.chartId eq chartId }
                .single()[VersionTable.id.count()]
                .toInt()

            val newIndex = existingCount + 1

            val insertedId = VersionTable.insertAndGetId {
                version.id?.let { vId -> it[id] = vId }
                it[VersionTable.chartId] = chartId
                it[bundleUrl] = version.bundleUrl
                it[previewUrl] = version.previewUrl
                it[duration] = version.duration
                it[difficulty] = version.difficulty
                it[notesAmount] = version.notesAmount
                it[effectsAmount] = version.effectsAmount
                it[bpm] = version.bpm
                it[isDeluxe] = version.isDeluxe
                it[isExplicit] = version.isExplicit
                it[changelog] = version.changelog.map { entry -> Changelog(UUID.randomUUID(), entry) }
            }

            val updated = ChartTable.update({ ChartTable.id eq chartId }) {
                it[latestVersionId] = insertedId
            }

            if (updated == 0) throw IllegalStateException("Chart $chartId not found — latestVersionId not updated")

            val row = VersionTable
                .selectAll()
                .where { VersionTable.id eq insertedId }
                .single()

            rowToVersion(row, newIndex)
        }

    /**
     * Removes a version from a chart, enforcing two invariants:
     *
     *  1. Only the latest version can be removed (prevents gaps in the chain).
     *  2. The chart must retain at least one version.
     *
     * After deletion, latestVersionId is updated to the next most recent version.
     */
    override suspend fun removeVersion(versionId: ULong, currentLatestVersionId: String?, versionCount: Int): Boolean =
        newSuspendedTransaction {
            if (currentLatestVersionId != versionId.toString()) {
                throw IllegalArgumentException(
                    "Only the latest version can be removed. " +
                            "Attempted $versionId but latest is $currentLatestVersionId"
                )
            }

            if (versionCount == 1) {
                throw IllegalArgumentException("Cannot remove the only version of chart")
            }

            // Confirm the version exists and get its chartId before deletion
            val versionRow = VersionTable
                .selectAll()
                .where { VersionTable.id eq versionId }
                .singleOrNull() ?: throw IllegalArgumentException("Version $versionId not found")

            val chartId = versionRow[VersionTable.chartId].value

            val newLatestRow = VersionTable
                .selectAll()
                .where {
                    (VersionTable.chartId eq chartId) and
                            (VersionTable.id neq versionId)
                }
                .orderBy(VersionTable.createdAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull() ?: throw IllegalStateException("No remaining versions found")

            ChartTable.update({ ChartTable.id eq chartId }) {
                it[ChartTable.latestVersionId] = newLatestRow[VersionTable.id]
            }

            VersionTable.deleteWhere { VersionTable.id eq versionId }

            true
        }
}