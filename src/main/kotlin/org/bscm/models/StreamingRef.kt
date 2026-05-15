package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.StreamingPlatform

@Serializable
data class StreamingRef(
    val platform: StreamingPlatform,
    val externalId: String
)