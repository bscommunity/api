package org.bscm.models.tables

import kotlinx.serialization.json.Json
import org.bscm.models.StreamingLink
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.json.json

object ChartTable : UUIDTable("chart") {
    val artist = varchar("artist", 255).index()
    val track = varchar("track", 255).index()
    val album = varchar("album", 255).nullable().index()
    val trackUrls = json<List<StreamingLink>>("track_urls", Json.Default)
    val trackPreviewUrl = varchar("track_preview_url", 255).nullable()
    val coverUrl = varchar("cover_url", 255)
    val difficulty = enumerationByName("difficulty", 10, Difficulty::class)
    val genre = enumerationByName("genres", 20, Genre::class).nullable()
    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)
    val isFeatured = bool("is_featured").default(false)

    val latestVersionId = reference("latest_version_id", VersionTable, onDelete = ReferenceOption.SET_NULL)
        .nullable()
        .index()
}