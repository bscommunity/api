package org.bscm.assets

import java.util.*

object TrackAssets {
    private const val CDN_URL = "https://bscm-assets.s3.amazonaws.com"

    fun cover(trackId: UUID): String =
        "$CDN_URL/tracks/$trackId/cover.webp"

    fun preview(trackId: UUID): String =
        "$CDN_URL/tracks/$trackId/preview.opus"
}