package org.bscm.models.dao

import org.bscm.models.tables.TrackStreamingRefTable
import org.bscm.models.tables.TrackTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
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

    val streamingRefs by TrackStreamingRefEntity referrersOn TrackStreamingRefTable.trackId
}