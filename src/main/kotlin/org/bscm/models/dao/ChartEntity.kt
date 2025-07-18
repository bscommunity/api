package org.bscm.models.dao

import org.bscm.models.tables.ChartStreamingLinkTable
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContributorTable
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class ChartEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ChartEntity>(ChartTable)

    var artist by ChartTable.artist
    var track by ChartTable.track
    var album by ChartTable.album
    var genre by ChartTable.genre

    var normalizedArtist by ChartTable.normalizedArtist
    var normalizedTrack by ChartTable.normalizedTrack
    var normalizedAlbum by ChartTable.normalizedAlbum

    var trackPreviewUrl by ChartTable.trackPreviewUrl
    var coverUrl by ChartTable.coverUrl
    var isFeatured by ChartTable.isFeatured
    var isPublic by ChartTable.isPublic

    var latestVersion by VersionEntity optionalReferencedOn ChartTable.latestVersionId

    // Updated: Many-to-many relationship through junction table
    val trackUrls by StreamingLinkEntity.via(ChartStreamingLinkTable.chartId, ChartStreamingLinkTable.streamingLinkId)

    val versions by VersionEntity referrersOn VersionTable.chartId
    val contributors by ContributorEntity referrersOn ContributorTable.chartId
}