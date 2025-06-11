package org.bscm.models

import kotlinx.serialization.Serializable
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
    val genre: Genre? = null,
    val coverUrl: String,
    val trackPreviewUrl: String? = null,
    val trackUrls: List<StreamingLink> = emptyList(),
    val isExplicit: Boolean,
    val isFeatured: Boolean,
    val latestVersion: Version? = null,
    val downloadsSum: Int? = null, // Room Database (from the Android app) expects simple fields to query
    @Serializable(with = LocalDateSerializer::class)
    val latestPublishedAt: LocalDate? = null, // Room Database (from the Android app) expects simple fields to query
    val contributors: List<Contributor> = emptyList()
)