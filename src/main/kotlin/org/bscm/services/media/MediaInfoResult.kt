package org.bscm.services.media

import org.bscm.models.StreamingLink
import org.bscm.models.enums.Genre
import org.bscm.models.enums.PreviewProvider

data class MediaInfoResult(
    val coverUrl: String?,
    val album: String?,
    val track: String,
    val artist: String,
    val genre: Genre?,
    val link: StreamingLink,
    val previewProvider: PreviewProvider?,
    val previewProviderTrackId: String?,
    val isExplicit: Boolean
)