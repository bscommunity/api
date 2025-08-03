// @file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty
import org.bscm.serialization.LocalDateSerializer
import java.time.LocalDate

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
    @Serializable(with = LocalDateSerializer::class)
    val publishedAt: LocalDate,
)