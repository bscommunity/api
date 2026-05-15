@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.Difficulty
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
@SerialName("chart")
data class Chart(
    override val id: String,
    override val contentId: String,
    override val type: CatalogItemType = CatalogItemType.CHART,
    override val status: CatalogItemStatus,

    override val isPublic: Boolean,
    override val isFeatured: Boolean,

    override val downloadsSum: Int,

    override val contributors: List<Contributor>,

    override val createdAt: LocalDateTime,
    override val publishedAt: LocalDateTime?,
    override val updatedAt: LocalDateTime?,

    override val likedAt: LocalDateTime?,
    override val bookmarkedAt: LocalDateTime?,

    override val previewVideoId: String?,

    val track: Track,

    val versionsCount: Int,

    val difficulty: Difficulty,
    val notesAmount: Int,
    val effectsAmount: Int,

    val isDeluxe: Boolean,
    val isExplicit: Boolean,

    val latestVersion: Version?
) : CatalogItem