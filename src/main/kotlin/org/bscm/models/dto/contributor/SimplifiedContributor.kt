package org.bscm.models.dto.contributor

import kotlinx.serialization.Serializable
import org.bscm.models.enums.ContributorRole
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class SimplifiedContributor(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val role: ContributorRole
)
