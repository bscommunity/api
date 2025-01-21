package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class Chart(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val artist: String,
    val name: String,
    val coverUrl: String,
    val duration: Int,
    val notesAmount: Int,
    val isDeluxe: Boolean,
    val isExplicit: Boolean,
    val isFeatured: Boolean
) {
    companion object {
        fun create(
            artist: String,
            name: String,
            coverUrl: String,
            duration: Int,
            notesAmount: Int,
            isDeluxe: Boolean,
            isExplicit: Boolean,
            isFeatured: Boolean
        ): Chart {
            return Chart(
                id = UUID.randomUUID(), // Auto-generate UUID
                artist = artist,
                name = name,
                coverUrl = coverUrl,
                duration = duration,
                notesAmount = notesAmount,
                isDeluxe = isDeluxe,
                isExplicit = isExplicit,
                isFeatured = isFeatured
            )
        }
    }
}