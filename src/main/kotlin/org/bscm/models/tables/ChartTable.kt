package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable

object ChartTable : UUIDTable("chart") {
    val artist = varchar("artist", 255)
    val name = varchar("name", 255)
    val coverUrl = varchar("cover_url", 255)
    val duration = integer("duration")
    val notesAmount = integer("notes_amount")
    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)
    val isFeatured = bool("is_featured").default(false)
}