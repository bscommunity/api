package org.bscm.repository

import org.bscm.models.Contributor
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.enums.ContributorRole
import java.util.*

interface ContributorRepository {
    suspend fun addContributors(chartId: UUID, contributors: List<SimplifiedContributor>): List<Contributor>
    suspend fun removeContributor(chartId: UUID, userId: UUID): Boolean
    suspend fun updateContributorRoles(chartId: UUID, userId: UUID, roles: List<ContributorRole>): Contributor
    suspend fun getContributors(chartId: UUID): List<UUID>
}
