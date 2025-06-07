package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.StreamingPlatform

@Serializable
data class StreamingLink(
    val platform: StreamingPlatform,
    val url: String
)