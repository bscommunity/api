package org.bscm.models.tables

import org.bscm.models.enums.StreamingPlatform
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object TrackStreamingRefTable : LongIdTable("track_streaming_refs") {
    val trackId = reference("track_id", TrackTable, onDelete = ReferenceOption.CASCADE)
    val platform = enumerationByName("platform", 30, StreamingPlatform::class)
    val externalId = varchar("external_id", 128).uniqueIndex()

    init {
        uniqueIndex(trackId, platform)
        uniqueIndex(platform, externalId)
    }
}