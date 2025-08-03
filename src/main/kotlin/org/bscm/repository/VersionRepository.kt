package org.bscm.repository

import org.bscm.models.Chart
import org.bscm.models.Version
import org.bscm.models.dto.version.CreateVersionRequest

interface VersionRepository {
    suspend fun getVersionById(id: ULong): Version?
    suspend fun addVersion(chartId: ULong, version: CreateVersionRequest): Version
    suspend fun addVersion(chart: Chart, version: CreateVersionRequest): Version
    suspend fun removeVersion(versionId: ULong): Boolean
    suspend fun getVersions(chartId: ULong): List<Version>
    suspend fun getLatestVersionsByChartIds(chartIds: List<ULong>): List<Version>
}