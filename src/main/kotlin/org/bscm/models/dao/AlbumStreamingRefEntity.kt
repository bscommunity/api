package org.bscm.models.dao

import org.bscm.models.tables.AlbumStreamingRefTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

class AlbumStreamingRefEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AlbumStreamingRefEntity>(AlbumStreamingRefTable)

    var album by AlbumEntity referencedOn AlbumStreamingRefTable.albumId
    var platform by AlbumStreamingRefTable.platform
    var externalId by AlbumStreamingRefTable.externalId
}
