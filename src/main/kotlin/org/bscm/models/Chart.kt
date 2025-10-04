package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Genre
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class Chart(
    val id: String,
    val artist: String,
    val track: String,
    val album: String?,
    val genre: Genre? = null,
    val trackUrls: List<StreamingLink> = emptyList(),
    val trackPreviewUrl: String? = null,
    val versions: List<Version> = emptyList(),
    val contributors: List<Contributor> = emptyList(),
    val latestVersion: Version?, // Room database field

    override val shareId: String,
    override val coverUrl: String,
    override val isPublic: Boolean,
    override val isFeatured: Boolean,
    override val downloadsSum: Int,
    @Serializable(with = LocalDateTimeSerializer::class)
    override val latestPublishedAt: LocalDateTime,
) : CatalogItem