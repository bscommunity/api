package org.bscm.models.entities

import org.bscm.models.tables.ChartTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class ChartEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ChartEntity>(ChartTable)

    var artist by ChartTable.artist
    var name by ChartTable.name
    var coverUrl by ChartTable.coverUrl
    var duration by ChartTable.duration
    var notesAmount by ChartTable.notesAmount
    var isDeluxe by ChartTable.isDeluxe
    var isExplicit by ChartTable.isExplicit
    var isFeatured by ChartTable.isFeatured
}