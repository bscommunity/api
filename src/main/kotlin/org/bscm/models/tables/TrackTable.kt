package org.bscm.models.tables

import org.bscm.models.enums.Genre
import org.jetbrains.exposed.v1.core.EnumerationNameColumnType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable

object TrackTable : UUIDTable("tracks") {
    val title = varchar("title", 200)
    val artist = varchar("artist", 255)
    val albumId = reference("album_id", AlbumTable, onDelete = ReferenceOption.SET_NULL).nullable().index()
    val isrc = varchar("isrc", 15).nullable().index()
    val genres = array<Genre>("genres", EnumerationNameColumnType(Genre::class, 255), 255).nullable()
    val bpm = integer("bpm").nullable()
    val duration = float("duration")

    val normalizedTitle = varchar("normalized_title", 200).nullable().index()
    val normalizedArtist = varchar("normalized_artist", 200).nullable().index()
}
