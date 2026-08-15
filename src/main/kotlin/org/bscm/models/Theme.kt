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
@SerialName("theme")
data class Theme(
    val name: String,
    val replaces: String,
    val displayArtUrl: String? = null,
    val previewUrl: String? = null,
    val coverUrl: String? = null,

    override val contributors: List<Contributor> = emptyList(),

    override val createdAt: LocalDateTime,
    override val publishedAt: LocalDateTime?,
    override val updatedAt: LocalDateTime?,
    override val likedAt: LocalDateTime?,
    override val bookmarkedAt: LocalDateTime?,

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

    override val versionsCount: Int = 0,
    override val latestVersion: Version? = null,
    override val bundleHash: String? = null,
) : CatalogItem, Versionable
