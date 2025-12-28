package org.bscm.models.interfaces

import org.bscm.models.Chart
import org.bscm.models.Version
import org.bscm.models.dto.version.CreateVersionRequest

interface IVersionRepository {
    suspend fun getVersionById(id: ULong): Version?
    suspend fun addVersion(chartId: ULong, version: CreateVersionRequest): Version
    suspend fun addVersion(chart: Chart, version: CreateVersionRequest): Version
    suspend fun removeVersion(latestVersion: Version, versionId: ULong): Boolean
    suspend fun getVersions(chartId: ULong): List<Version>
    suspend fun getLatestVersionsByChartIds(chartIds: List<ULong>): List<Version>
}