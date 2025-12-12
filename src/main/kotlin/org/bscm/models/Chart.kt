@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.Genre
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

// SS = Server-side gathered fields for convenience

@Serializable
data class Chart(
    val artist: String,
    val track: String,
    val album: String?,
    val genre: Genre? = null,
    val remoteCoverUrl: String? = null,
    val trackUrls: List<StreamingLink> = emptyList(),
    val trackPreviewUrl: String? = null,
    val versions: List<Version> = emptyList(),
    val latestVersion: Version?, // SS

    override val contributors: List<Contributor> = emptyList(),

    override val isLiked: Boolean, // SS
    override val isFavorited: Boolean, // SS
    override val downloadsSum: Int, // SS
    override val latestPublishedAt: LocalDateTime, // SS

    override val id: String,
    override val contentId: String,
    override val coverUrl: String,
    override val isPublic: Boolean,
    override val isFeatured: Boolean,
) : CatalogItem