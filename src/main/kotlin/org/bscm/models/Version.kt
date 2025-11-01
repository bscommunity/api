@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.Difficulty
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

// SS = Server-side gathered fields for convenience

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
    val publishedAt: LocalDateTime,

    val isDeluxe: Boolean,
    val isExplicit: Boolean,

    val bundleUrl: String,
    val previewUrl: String? = null,

    val downloadsAmount: Int = 0, // SS
    val knownIssues: List<KnownIssue> = emptyList(), // SS
)