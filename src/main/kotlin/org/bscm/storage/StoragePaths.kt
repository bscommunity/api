package org.bscm.storage

import java.util.*

object StoragePaths {
    fun trackCover(trackId: UUID): String = "tracks/$trackId/cover.avif"

    fun trackPreview(trackId: UUID): String = "tracks/$trackId/preview.opus"

    fun themeCover(themeId: String): String = "themes/$themeId/cover.avif"

    fun themeSkin(themeId: String): String = "themes/$themeId/skin.avif"

    fun tourPassCover(tourPassId: String): String = "tour-passes/$tourPassId/cover.avif"
}
