package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Genre
import org.bscm.serialization.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class Chart(
    val id: String,
    val shareId: String,
    val artist: String,
    val track: String,
    val album: String?,
    val genre: Genre? = null,
    val coverUrl: String,
    val trackUrls: List<StreamingLink> = emptyList(),
    val trackPreviewUrl: String? = null,
    val isFeatured: Boolean,
    val versions: List<Version> = emptyList(),
    val contributors: List<Contributor> = emptyList(),
    // Room Database fields (Room expects simple fields to query)
    val downloadsSum: Int = 0,
    val latestVersion: Version?,
    @Serializable(with = LocalDateSerializer::class)
    val latestPublishedAt: LocalDate,
)