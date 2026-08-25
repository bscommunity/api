@file:UseSerializers(LocalDateTimeSerializer::class, UUIDSerializer::class)

package org.bscm.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Visibility
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
@SerialName("chart")
data class Chart(
    override val id: String,
    override val type: CatalogItemType = CatalogItemType.CHART,
    override val status: CatalogItemStatus,
    override val visibility: Visibility,

    override val isFeatured: Boolean,

    override val downloadsSum: Int,

    val likesCount: Int = 0,
    val bookmarksCount: Int = 0,

    override val contributors: List<Contributor>,

    override val createdAt: LocalDateTime,
    override val publishedAt: LocalDateTime?,
    override val updatedAt: LocalDateTime?,

    override val likedAt: LocalDateTime?,
    override val bookmarkedAt: LocalDateTime?,

    override val previewVideoId: String?,

    override val discordChannelId: String?,
    override val discordMessageId: String?,
    override val authorId: UUID?,

    val track: Track,

    override val versionsCount: Int,
    override val bundleHash: String?,

    val difficulty: Difficulty,
    val notesAmount: Int,
    val effectsAmount: Int,

    val isDeluxe: Boolean,
    val isExplicit: Boolean,

    val changelog: List<Changelog>,

    override val latestVersion: Version?
) : CatalogItem, Versionable