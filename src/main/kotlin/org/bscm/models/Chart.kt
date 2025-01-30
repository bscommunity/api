package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.enums.Difficulty
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class Chart(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val artist: String,
    val track: String,
    val album: String?,
    val coverUrl: String,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
    val isFeatured: Boolean,
    val versions: List<Version> = emptyList(),
    val contributors: List<Contributor> = emptyList()
) {
    companion object {
        fun create(
            artist: String,
            track: String,
            album: String,
            coverUrl: String,
            difficulty: Difficulty?,
            isDeluxe: Boolean,
            isExplicit: Boolean,
            isFeatured: Boolean
        ): Chart {
            return Chart(
                id = UUID.randomUUID(), // Auto-generate UUID
                artist = artist,
                track = track,
                album = album,
                coverUrl = coverUrl,
                difficulty = difficulty ?: Difficulty.Normal,
                isDeluxe = isDeluxe,
                isExplicit = isExplicit,
                isFeatured = isFeatured
            )
        }
    }
}