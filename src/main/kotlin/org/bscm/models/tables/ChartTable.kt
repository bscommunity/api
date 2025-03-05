package org.bscm.models.tables

import org.bscm.models.enums.Difficulty
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object ChartTable : UUIDTable("chart") {
    val artist = varchar("artist", 255)
    val track = varchar("track", 255)
    val album = varchar("album", 255).nullable()
    val coverUrl = varchar("cover_url", 255)
    val difficulty = enumerationByName("difficulty", 10, Difficulty::class)
    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)
    val isFeatured = bool("is_featured").default(false)
    val latestVersionId = reference("latest_version_id", VersionTable, onDelete = ReferenceOption.SET_NULL).nullable()
}