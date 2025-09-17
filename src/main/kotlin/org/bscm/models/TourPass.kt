package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class TourPass(
    val id: String,
    val shareId: String,
    val name: String,
    val artist: String?,
    val coverUrl: String,
    val charts: List<Chart>,
    val isPublic: Boolean,
    val isFeatured: Boolean,
    // Room Database fields (Room expects simple fields to query)
    val downloadsSum: Int = 0,
    @Serializable(with = LocalDateTimeSerializer::class)
    val latestPublishedAt: LocalDateTime,
)