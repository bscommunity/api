package org.bscm.models.dao

import org.bscm.models.tables.TrackStreamingRefTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TrackStreamingRefEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<TrackStreamingRefEntity>(TrackStreamingRefTable)

    var track by TrackEntity referencedOn TrackStreamingRefTable.trackId
    var platform by TrackStreamingRefTable.platform
    var externalId by TrackStreamingRefTable.externalId
}
