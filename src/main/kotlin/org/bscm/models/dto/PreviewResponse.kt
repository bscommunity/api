package org.bscm.models.dto

import kotlinx.serialization.Serializable
import org.bscm.models.enums.PreviewProvider
import org.bscm.serialization.InstantSerializer
import java.time.Instant

@Serializable
data class PreviewResponse(
    val url: String,
    val provider: PreviewProvider,
    @Serializable(with = InstantSerializer::class)
    val expiresAt: Instant? // null = URL estável
)