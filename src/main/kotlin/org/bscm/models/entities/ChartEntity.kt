package org.bscm.models.entities

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
    var trackUrl by ChartTable.trackUrl
    var trackPreviewUrl by ChartTable.trackPreviewUrl
    var coverUrl by ChartTable.coverUrl
    var isDeluxe by ChartTable.isDeluxe
    var difficulty by ChartTable.difficulty
    var isExplicit by ChartTable.isExplicit
    var isFeatured by ChartTable.isFeatured

    var latestVersion by VersionEntity optionalReferencedOn ChartTable.latestVersionId
    val versions by VersionEntity referrersOn VersionTable.chartId
    val contributors by ContributorEntity referrersOn ContributorTable.chartId
}