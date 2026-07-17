@file:UseSerializers(LocalDateTimeSerializer::class, UUIDSerializer::class)

package org.bscm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.Genre
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
@SerialName("track")
data class Track(
    val id: UUID,
    val title: String,
    val artist: String,
    val album: String?,
    val isrc: String?,
    val genres: List<Genre> = emptyList(),
    val bpm: Int? = null,
    val duration: Float,
    val streamingRefs: List<StreamingRef> = emptyList(),
    val coverUrl: String? = null,
    val previewUrl: String? = null,
)
