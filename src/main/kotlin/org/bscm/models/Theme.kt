package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class Theme(
    val name: String,
    val replaces: String,
    val previewUrl: String,

    override val isLiked: Boolean,
    override val isFavorited: Boolean,

    override val id: String,
    override val contentId: String,
    override val coverUrl: String,
    override val isPublic: Boolean,
    override val isFeatured: Boolean,
    override val downloadsSum: Int,
    @Serializable(with = LocalDateTimeSerializer::class)
    override val latestPublishedAt: LocalDateTime,
) : CatalogItem