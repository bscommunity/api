package org.bscm.models.tables

import org.bscm.models.enums.Genre
import org.jetbrains.exposed.sql.ReferenceOption

// CatalogItemTable brings contentId, shareId, coverUrl, isPublic, isFeatured, downloadsSum, latestPublishedAt
object ChartTable : CatalogItemTable("charts") {
    val artist = varchar("artist", 200)
    val track = varchar("track", 200)
    val album = varchar("album", 200).nullable()
    val genre = enumerationByName("genres", 20, Genre::class).nullable()
    val trackPreviewUrl = varchar("track_preview_url", 255).nullable()

    val normalizedArtist = varchar("normalized_artist", 200).nullable().index()
    val normalizedTrack = varchar("normalized_track", 200).nullable().index()
    val normalizedAlbum = varchar("normalized_album", 200).nullable().index()

    val latestVersionId = reference("latest_version_id", VersionTable, ReferenceOption.CASCADE).nullable()

    init {
        index(false, normalizedArtist, normalizedTrack, normalizedAlbum)
    }
}