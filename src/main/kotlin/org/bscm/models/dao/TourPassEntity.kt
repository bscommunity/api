package org.bscm.models.dao

import org.bscm.models.tables.TourPassTable
import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.ULongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TourPassEntity(id: EntityID<ULong>) : ULongEntity(id) {
    companion object : ULongEntityClass<TourPassEntity>(TourPassTable)

    var name by TourPassTable.name
    var artist by TourPassTable.artist
    var coverUrl by TourPassTable.coverUrl
    var isPublic by TourPassTable.isPublic
    var isFeatured by TourPassTable.isFeatured
    var downloadsSum by TourPassTable.downloadsSum
    var latestPublishedAt by TourPassTable.latestPublishedAt

    var content by ContentEntity referencedOn TourPassTable.contentId
}
