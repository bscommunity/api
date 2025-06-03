package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.StreamingPlatform
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class StreamingLink(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val chartId: UUID,
    val platform: StreamingPlatform,
    val url: String
)