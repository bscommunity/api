// @file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class Version(
    val id : String,
    val chartId: String,
    val index: Int,
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
    val bundleUrl: String,
    val previewUrl: String? = null,
    val downloadsAmount: Int = 0,
    val knownIssues: List<KnownIssue> = emptyList(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val publishedAt: LocalDateTime,
)