package org.bscm.models.interfaces

import org.bscm.models.Changelog
import java.util.*

interface IChangelogRepository {
    suspend fun addIssue(catalogItemId: String, description: String): UUID
    suspend fun removeIssue(catalogItemId: String, issueId: UUID): Boolean
    suspend fun getByCatalogItemIds(catalogItemIds: List<String>): Map<String, List<Changelog>>
}
