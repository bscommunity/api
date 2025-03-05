package org.bscm.models.dto.contributor

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ContributorRole

@Serializable
data class UpdateContributorRequest(
    val roles: List<ContributorRole>
)
