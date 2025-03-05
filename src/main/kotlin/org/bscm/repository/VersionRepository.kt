package org.bscm.repository

import org.bscm.models.Version
import java.util.*

interface VersionRepository {
    suspend fun addVersion(version: Version): Version
    suspend fun removeVersion(versionId: Int): Boolean
    suspend fun getVersions(chartId: UUID): List<Version>
}