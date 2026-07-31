package org.bscm.models.dto.chart

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bscm.models.StreamingRef
import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import java.util.*

@Serializable
data class CreateChartRequest (
    val artist: String,
    val track: String,
    val album: String? = null,
    @Contextual val albumId: UUID? = null,
    val trackUrls: List<StreamingRef>,
    val previewUrl: String? = null,
    val trackPreviewUrl: String? = null,
    val coverUrl: String,
    val genres: List<Genre> = emptyList(),
    val isExplicit: Boolean,
    val contributors: List<SimplifiedContributor> = emptyList(),

    // Server-side properties
    val versionId: ULong? = null,
    val catalogId: String? = null,

    // First version properties
    val duration: Float,
    val notesAmount: Int,
    val effectsAmount: Int,
    val bpm: Int,
    val difficulty: Difficulty,
    val isDeluxe: Boolean,
    val bundleUrl: String,
    val fileSizeBytes: Long = 0,
    val bundleHash: String? = null,
    val isrc: String? = null,
)
