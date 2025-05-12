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
    val album: String?,
    val coverUrl: String,
    val trackUrls: List<StreamingLink> = emptyList(),
    val trackPreviewUrl: String? = null,
    val difficulty: Difficulty,
    val genre: Genre? = null,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
    val isFeatured: Boolean,
    val isPublic: Boolean,
    val latestVersion: Version? = null,
    val versions: List<Version> = emptyList(),
    val contributors: List<Contributor> = emptyList()
)