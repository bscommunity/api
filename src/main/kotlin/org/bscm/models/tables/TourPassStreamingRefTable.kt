package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object TourPassStreamingRefTable : LongIdTable("tour_pass_streaming_refs") {
    val tourPassId = reference("tour_pass_id", TourPassTable, onDelete = ReferenceOption.CASCADE)
    val platform = varchar("platform", 30)
    val externalId = varchar("external_id", 128).uniqueIndex()

    init {
        uniqueIndex(tourPassId, platform)
        uniqueIndex(platform, externalId)
    }
}

