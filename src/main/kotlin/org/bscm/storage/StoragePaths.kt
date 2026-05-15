package org.bscm.storage

import java.util.*

object StoragePaths {
    fun trackCover(trackId: UUID): String = "tracks/$trackId/cover.avif"

    fun trackPreview(trackId: UUID): String = "tracks/$trackId/preview.opus"
}

