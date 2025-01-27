// @file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class Version(
    val id: Int,
    @Serializable(with = UUIDSerializer::class)
    val chartId: UUID,
    val index: Int,
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
    val chartUrl: String,
    val downloadsAmount: Int = 0,
    val knownIssues: List<KnownIssue> = emptyList(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val publishedAt: LocalDateTime,
)