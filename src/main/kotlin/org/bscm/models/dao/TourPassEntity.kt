package org.bscm.models.dao

import org.bscm.models.tables.TourPassStreamingLinkTable
import org.bscm.models.tables.TourPassTable
import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.ULongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TourPassEntity(id: EntityID<ULong>) : ULongEntity(id) {
    companion object : ULongEntityClass<TourPassEntity>(TourPassTable)

    var authorId by TourPassTable.authorId
    var name by TourPassTable.name
    var description by TourPassTable.description
    var artist by TourPassTable.artist
    var coverUrl by TourPassTable.coverUrl
    val playlistUrls by StreamingLinkEntity.via(TourPassStreamingLinkTable.tourPassId, TourPassStreamingLinkTable.streamingLinkId)
    var isPublic by TourPassTable.isPublic
    var isFeatured by TourPassTable.isFeatured
    var downloadsSum by TourPassTable.downloadsSum

    var createdAt by TourPassTable.createdAt
    var latestPublishedAt by TourPassTable.latestUpdatedAt

    var content by ContentEntity referencedOn TourPassTable.contentId
}
