package org.bscm.repository

import org.bscm.models.Version
import org.bscm.models.dto.version.CreateVersionRequest
import java.util.*

interface VersionRepository {
    suspend fun addVersion(version: CreateVersionRequest): Version
    suspend fun removeVersion(index: Int, chartId: UUID): Boolean
    suspend fun removeVersion(versionId: UUID): Boolean
    suspend fun getVersions(chartId: UUID): List<Version>
    suspend fun getLatestVersionsByChartIds(chartIds: List<UUID>): List<Version>
}