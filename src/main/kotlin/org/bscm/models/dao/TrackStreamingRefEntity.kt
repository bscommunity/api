package org.bscm.models.dao

import org.bscm.models.tables.TrackStreamingRefTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

class TrackStreamingRefEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<TrackStreamingRefEntity>(TrackStreamingRefTable)

    var track by TrackEntity referencedOn TrackStreamingRefTable.trackId
    var platform by TrackStreamingRefTable.platform
    var externalId by TrackStreamingRefTable.externalId
}
