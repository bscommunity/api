@file:UseSerializers(LocalDateTimeSerializer::class, UUIDSerializer::class)

package org.bscm.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.Visibility
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
@SerialName("tour_pass")
data class TourPass(
    val name: String,
    val description: String? = null,
    val artist: String? = null,
    val charts: List<Chart>,
    val coverUrl: String? = null,

    override val contributors: List<Contributor> = emptyList(),

    override val createdAt: LocalDateTime,
    override val publishedAt: LocalDateTime?,
    override val updatedAt: LocalDateTime?,
    override val likedAt: LocalDateTime? = null,
    override val bookmarkedAt: LocalDateTime? = null,

    val likesCount: Int = 0,
    val bookmarksCount: Int = 0,

    override val id: String,
    override val type: CatalogItemType,
    override val status: CatalogItemStatus,
    override val visibility: Visibility,
    override val isFeatured: Boolean,
    override val downloadsSum: Int,
    override val previewVideoId: String?,

    override val discordChannelId: String?,
    override val discordMessageId: String?,
    override val authorId: UUID?,
) : CatalogItem
