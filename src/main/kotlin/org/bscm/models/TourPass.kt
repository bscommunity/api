@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class TourPass(
    val name: String,
    val artist: String?,
    val charts: List<Chart>,

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