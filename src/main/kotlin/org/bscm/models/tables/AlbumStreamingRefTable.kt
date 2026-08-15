package org.bscm.models.tables

import org.bscm.models.enums.StreamingPlatform
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object AlbumStreamingRefTable : LongIdTable("album_streaming_refs") {
    val albumId = reference("album_id", AlbumTable, onDelete = ReferenceOption.CASCADE)
    val platform = enumerationByName("platform", 30, StreamingPlatform::class)
    val externalId = varchar("external_id", 128).uniqueIndex()

    init {
        uniqueIndex(albumId, platform)
        uniqueIndex(platform, externalId)
    }
}
