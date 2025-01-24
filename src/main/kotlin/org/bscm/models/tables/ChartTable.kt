package org.bscm.models.tables

import org.bscm.models.enums.Difficulty
import org.jetbrains.exposed.dao.id.UUIDTable

object ChartTable : UUIDTable("chart") {
    val artist = varchar("artist", 255)
    val track = varchar("track", 255)
    val coverUrl = varchar("cover_url", 255)
    val difficulty = enumerationByName("difficulty", 255, Difficulty::class)
    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)
    val isFeatured = bool("is_featured").default(false)
}