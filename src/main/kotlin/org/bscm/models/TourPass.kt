@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
@SerialName("tour_pass")
data class TourPass(
    val name: String,
    val description: String? = null,
    val artist: String?,
    val charts: List<Chart>,
    val playlistUrls: List<StreamingRef> = emptyList(),

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