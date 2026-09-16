package org.bscm.models.interfaces

import org.bscm.models.ContributorWithRoles
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.enums.ContributorRole
import java.util.*

interface IContributorRepository {
    suspend fun addContributors(catalogItemId: String, contributors: List<SimplifiedContributor>): List<ContributorWithRoles>
    suspend fun removeContributor(catalogItemId: String, userId: UUID, role: ContributorRole? = null): Boolean
    suspend fun updateContributorRoles(catalogItemId: String, userId: UUID, roles: List<ContributorRole>): List<ContributorWithRoles>
    suspend fun getContributors(catalogItemId: String): List<ContributorWithRoles>
}
