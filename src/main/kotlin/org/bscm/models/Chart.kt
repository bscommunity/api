package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class Chart(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val artist: String,
    val track: String,
    val genre: Genre? = null,
    val coverUrl: String,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
    val isFeatured: Boolean,
    val isPublic: Boolean,
    val versions: List<Version> = emptyList(),
    val contributors: List<Contributor> = emptyList()
)