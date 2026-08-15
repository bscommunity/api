package org.bscm.models.dao

import org.bscm.models.tables.AlbumTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

class AlbumEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AlbumEntity>(AlbumTable)

    var name by AlbumTable.name
    var normalizedName by AlbumTable.normalizedName
}
