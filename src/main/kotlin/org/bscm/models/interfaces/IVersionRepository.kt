package org.bscm.models.interfaces

import org.bscm.models.ChartVersion
import org.bscm.models.dto.version.CreateVersionRequest

interface IVersionRepository {
    suspend fun getVersionById(id: ULong): ChartVersion?
    suspend fun getVersions(chartId: ULong): List<ChartVersion>
    suspend fun getLatestVersionsByChartIds(chartIds: List<ULong>): List<ChartVersion>

    suspend fun addVersion(chartId: ULong, version: CreateVersionRequest): ChartVersion

    /**
     * Removes a version by its ID.
     *
     * The repository owns all invariant checks internally:
     *  - Must be the latest version of its chart.
     *  - Chart must have more than one version.
     */
    suspend fun removeVersion(
        versionId: ULong,
        currentLatestVersionId: String?,
        versionCount: Int
    ): Boolean
}