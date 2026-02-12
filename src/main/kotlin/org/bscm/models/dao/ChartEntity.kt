package org.bscm.models.dao

import org.bscm.models.tables.ChartStreamingLinkTable
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContributorTable
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.ULongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ChartEntity(id: EntityID<ULong>) : ULongEntity(id) {
    companion object : ULongEntityClass<ChartEntity>(ChartTable)

    var contentId by ChartTable.contentId
    var authorId by ChartTable.authorId
    val contributors by ContributorEntity referrersOn ContributorTable.chartId

    var artist by ChartTable.artist
    var track by ChartTable.track
    var album by ChartTable.album
    var genre by ChartTable.genre
    var trackPreviewUrl by ChartTable.trackPreviewUrl

    var normalizedArtist by ChartTable.normalizedArtist
    var normalizedTrack by ChartTable.normalizedTrack
    var normalizedAlbum by ChartTable.normalizedAlbum

    var coverUrl by ChartTable.coverUrl
    var isPublic by ChartTable.isPublic
    var isFeatured by ChartTable.isFeatured
    var downloadsSum by ChartTable.downloadsSum

    var createdAt by ChartTable.createdAt
    var latestUpdatedAt by ChartTable.latestUpdatedAt

    // Updated: Many-to-many relationship through junction table
    val trackUrls by StreamingLinkEntity.via(ChartStreamingLinkTable.chartId, ChartStreamingLinkTable.streamingLinkId)

    val versions by VersionEntity referrersOn VersionTable.chartId
    var latestVersion by VersionEntity optionalReferencedOn ChartTable.latestVersionId
}