package org.bscm.models.dao

import org.bscm.models.tables.TourPassStreamingRefTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TourPassStreamingRefEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<TourPassStreamingRefEntity>(TourPassStreamingRefTable)

    var platform by TourPassStreamingRefTable.platform
    var externalId by TourPassStreamingRefTable.externalId
}
