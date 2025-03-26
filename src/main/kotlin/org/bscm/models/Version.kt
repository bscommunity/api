// @file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDate
import java.util.*

@Serializable
data class Version(
    @Serializable(with = UUIDSerializer::class)
    val id : UUID,
    val index: Int,
    @Serializable(with = UUIDSerializer::class)
    val chartId: UUID,
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
    val chartUrl: String,
    val chartPreviewUrls: List<String>? = null,
    val downloadsAmount: Int = 0,
    val knownIssues: List<KnownIssue> = emptyList(),
    @Serializable(with = LocalDateSerializer::class)
    val publishedAt: LocalDate,
)