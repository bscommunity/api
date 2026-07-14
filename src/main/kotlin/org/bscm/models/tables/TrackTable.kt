package org.bscm.models.tables

import org.bscm.models.enums.Genre
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable

object TrackTable : UUIDTable("tracks") {
    val title = varchar("title", 200)
    val artist = varchar("artist", 100)
    val album = varchar("album", 200).nullable()
    val isrc = varchar("isrc", 15).nullable()
    val genre = enumerationByName("genres", 20, Genre::class).nullable()
    val bpm = integer("bpm").nullable()
    val duration = float("duration")

    val normalizedTitle = varchar("normalized_title", 200).nullable().index()
    val normalizedArtist = varchar("normalized_artist", 200).nullable().index()
    val normalizedAlbum = varchar("normalized_album", 200).nullable().index()
}