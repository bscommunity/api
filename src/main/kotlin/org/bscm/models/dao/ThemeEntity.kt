package org.bscm.models.dao

import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.ContributorTable
import org.bscm.models.tables.ThemeTable
import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.ULongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ThemeEntity(id: EntityID<ULong>) : ULongEntity(id) {
    companion object : ULongEntityClass<ThemeEntity>(ThemeTable)

    var contentId by ChartTable.contentId
    val contributors by ContributorEntity referrersOn ContributorTable.chartId

    var name by ThemeTable.name
    var replaces by ThemeTable.replaces
    var previewUrl by ThemeTable.previewUrl

    var coverUrl by ThemeTable.coverUrl
    var isPublic by ThemeTable.isPublic
    var isFeatured by ThemeTable.isFeatured

    var downloadsSum by ThemeTable.downloadsSum
    var latestPublishedAt by ThemeTable.latestUpdatedAt

    // var content by ContentEntity referencedOn ThemeTable.contentId
}
