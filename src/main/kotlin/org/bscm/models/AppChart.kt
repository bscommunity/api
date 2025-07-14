package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.bscm.serialization.LocalDateSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDate
import java.util.*

@Serializable
data class AppChart (
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
    val latestVersion: Version,
    val downloadsSum: Int = 0, // Room Database (from the Android app) expects simple fields to query
    @Serializable(with = LocalDateSerializer::class)
    val latestPublishedAt: LocalDate, // Room Database (from the Android app) expects simple fields to query
    val contributors: List<Contributor> = emptyList()
)