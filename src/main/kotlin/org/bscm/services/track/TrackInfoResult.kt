package org.bscm.services.track

import org.bscm.models.StreamingRef
import org.bscm.models.enums.Genre
import org.bscm.models.enums.PreviewProvider

data class TrackInfoResult(
    val coverUrl: String?,
    val album: String?,
    val track: String,
    val artist: String,
    val genre: Genre?,
    val link: StreamingRef,
    val previewProvider: PreviewProvider?,
    val previewProviderTrackId: String?,
    val isExplicit: Boolean,
    val isrc: String? = null
)