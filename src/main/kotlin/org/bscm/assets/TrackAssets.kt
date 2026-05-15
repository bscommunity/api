package org.bscm.assets

import org.bscm.storage.StoragePaths
import java.util.*

object TrackAssets {
    private const val CDN_URL = "https://bscm-assets.s3.amazonaws.com"

    fun cover(trackId: UUID): String =
        "$CDN_URL/${StoragePaths.trackCover(trackId)}"

    fun preview(trackId: UUID): String =
        "$CDN_URL/${StoragePaths.trackPreview(trackId)}"
}