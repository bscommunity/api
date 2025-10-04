package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class Theme(
    val id: String,
    val name: String,
    val replaces: String,
    val previewUrl: String,

    override val shareId: String,
    override val coverUrl: String,
    override val isPublic: Boolean,
    override val isFeatured: Boolean,
    override val downloadsSum: Int,
    @Serializable(with = LocalDateTimeSerializer::class)
    override val latestPublishedAt: LocalDateTime,
) : CatalogItem