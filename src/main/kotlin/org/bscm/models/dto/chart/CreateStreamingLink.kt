package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable
import org.bscm.models.enums.StreamingPlatform

@Serializable
data class CreateStreamingLink (
    val platform: StreamingPlatform,
    val url: String,
)