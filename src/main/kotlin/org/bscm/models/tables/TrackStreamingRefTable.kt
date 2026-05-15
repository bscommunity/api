package org.bscm.models.tables

import org.bscm.models.enums.StreamingPlatform
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object TrackStreamingRefTable : LongIdTable("track_streaming_refs") {
    val trackId = reference("track_id", TrackTable, onDelete = ReferenceOption.CASCADE)
    val platform = enumerationByName("platform", 30, StreamingPlatform::class)
    val externalId = varchar("external_id", 128).uniqueIndex()

    init {
        uniqueIndex(trackId, platform)
        uniqueIndex(platform, externalId)
    }
}