package org.bscm.storage

import java.util.*

object StoragePaths {
    fun trackCover(trackId: UUID): String = "tracks/$trackId/cover.avif"

    fun trackPreview(trackId: UUID): String = "tracks/$trackId/preview.opus"

    fun assetCover(key: String): String = "assets/$key/cover.png"

    fun themeDisplay(themeId: String): String = "themes/$themeId/display.png"

    fun tourPassCover(tourPassId: String): String = "tour-passes/$tourPassId/cover.png"
}

