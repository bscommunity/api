package org.bscm.models.tables

import org.bscm.models.enums.Genre
import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ChartTable : ULongIdTable("charts") {
    val artist = varchar("artist", 200)
    val track = varchar("track", 200)
    val album = varchar("album", 200).nullable()
    val genre = enumerationByName("genres", 20, Genre::class).nullable()

    val shareId = varchar("shareId", 11).uniqueIndex()

    val normalizedArtist = varchar("normalized_artist", 200).nullable().index()
    val normalizedTrack = varchar("normalized_track", 200).nullable().index()
    val normalizedAlbum = varchar("normalized_album", 200).nullable().index()

    val coverUrl = varchar("cover_url", 255)
    val trackPreviewUrl = varchar("track_preview_url", 255).nullable()

    val isFeatured = bool("is_featured").default(false)
    val isPublic = bool("is_public").default(true)

    val latestVersionId = reference("latest_version_id", VersionTable, ReferenceOption.CASCADE).nullable()

    init {
        index("idx_chart_search", false, normalizedArtist, normalizedTrack, normalizedAlbum)
    }
}