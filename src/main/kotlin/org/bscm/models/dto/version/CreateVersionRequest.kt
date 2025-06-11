package org.bscm.models.dto.version

import kotlinx.serialization.Serializable
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class CreateVersionRequest(
    @Serializable(with = UUIDSerializer::class)
    val chartId: UUID,
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
    val chartUrl: String,
    val chartPreviewUrl: String? = null
)