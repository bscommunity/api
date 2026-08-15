package org.bscm.models.dto.changelog

import kotlinx.serialization.Serializable
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class CreateChangelogEntryResponse(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID
)