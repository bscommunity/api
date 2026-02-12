@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

// SS = Server-side gathered fields for convenience

@Serializable
data class Theme(
    val name: String,
    val replaces: String,
    val previewUrl: String,

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