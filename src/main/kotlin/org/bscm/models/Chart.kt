@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.Genre
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

// SS = Server-side gathered fields for convenience

@Serializable
@SerialName("chart")
data class Chart(
    val artist: String,
    val track: String,
    val album: String?,
    val genre: Genre? = null,

    val trackUrls: List<StreamingRef> = emptyList(),
    val trackPreviewUrl: String? = null,



    val versions: List<Version> = emptyList(),

    override val contributors: List<Contributor> = emptyList(),

    override val createdAt: LocalDateTime,
    override val updatedAt: LocalDateTime, // SS
    override val likedAt: LocalDateTime?, // SS
    override val bookmarkedAt: LocalDateTime?, // SS

    override val id: String,
    override val contentId: String,
    override val coverUrl: String,
    override val isPublic: Boolean,
    override val isFeatured: Boolean,
    override val downloadsSum: Int, // SS
) : CatalogItem