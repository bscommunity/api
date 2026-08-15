package org.bscm.storage

import java.util.*

object StoragePaths {
    fun albumCover(albumId: UUID): String = "albums/$albumId/cover.avif"

    fun trackPreview(trackId: UUID): String = "tracks/$trackId/preview.opus"

    fun themeCover(themeId: String): String = "themes/$themeId/cover.avif"

    fun themeDisplay(themeId: String): String = "themes/$themeId/display.avif"

    fun tourPassCover(tourPassId: String): String = "tour-passes/$tourPassId/cover.avif"
}
