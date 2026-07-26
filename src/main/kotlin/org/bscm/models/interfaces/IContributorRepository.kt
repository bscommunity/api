package org.bscm.models.interfaces

import org.bscm.models.Contributor
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.enums.ContributorRole
import java.util.*

interface IContributorRepository {
    suspend fun addContributors(catalogItemId: String, contributors: List<SimplifiedContributor>): List<Contributor>
    suspend fun removeContributor(catalogItemId: String, userId: UUID, role: ContributorRole? = null): Boolean
    suspend fun updateContributorRoles(catalogItemId: String, userId: UUID, roles: List<ContributorRole>): List<Contributor>
    suspend fun getContributors(catalogItemId: String): List<Contributor>
}
