@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

// SS = Server-side gathered fields for convenience

@Serializable
@SerialName("theme")
data class Theme(
    val name: String,
    val description: String? = null,
    val replaces: String,
    val previewUrl: String,
    val displayArtUrl: String,

    override val contributors: List<Contributor> = emptyList(),

    override val createdAt: LocalDateTime,
    override val publishedAt: LocalDateTime?,
    override val updatedAt: LocalDateTime?,
    override val likedAt: LocalDateTime?,
    override val bookmarkedAt: LocalDateTime?,

    override val id: String,
    override val contentId: String,
    override val type: CatalogItemType,
    override val status: CatalogItemStatus,
    override val isPublic: Boolean,
    override val isFeatured: Boolean,
    override val downloadsSum: Int,
    override val previewVideoId: String?,

    val coverUrl: String,
) : CatalogItem