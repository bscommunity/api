package org.bscm.models.interfaces

import org.bscm.models.Version
import org.bscm.models.dto.version.CreateVersionRequest

interface IVersionRepository {
    suspend fun getVersionById(id: ULong): Version?
    suspend fun getVersions(catalogItemId: String): List<Version>
    suspend fun getLatestVersionsByChartIds(chartIds: List<ULong>): List<Version>

    suspend fun addVersion(catalogItemId: String, version: CreateVersionRequest): Version

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