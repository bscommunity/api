package org.bscm.models.dao

import org.bscm.models.tables.TrackStreamingRefTable
import org.bscm.models.tables.TrackTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

class TrackEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TrackEntity>(TrackTable)

    var title by TrackTable.title
    var artist by TrackTable.artist
    var album by TrackTable.album
    var isrc by TrackTable.isrc
    var genre by TrackTable.genre
    var bpm by TrackTable.bpm
    var duration by TrackTable.duration
    var normalizedTitle by TrackTable.normalizedTitle
    var normalizedArtist by TrackTable.normalizedArtist
    var normalizedAlbum by TrackTable.normalizedAlbum

    val streamingRefs by TrackStreamingRefEntity referrersOn TrackStreamingRefTable.trackId
}