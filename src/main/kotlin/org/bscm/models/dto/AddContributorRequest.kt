package org.bscm.models.dto

import kotlinx.serialization.Serializable
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class AddContributorRequest(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID
)