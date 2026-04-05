package org.bscm.models.dao

import org.bscm.models.tables.ThemeTable
import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.ULongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ThemeEntity(id: EntityID<ULong>) : ULongEntity(id) {
    companion object : ULongEntityClass<ThemeEntity>(ThemeTable)

    var contentId by ThemeTable.contentId
    var authorId by ThemeTable.authorId

    var name by ThemeTable.name
    var replaces by ThemeTable.replaces
    var displayArtUrl by ThemeTable.displayArtUrl
    var previewUrl by ThemeTable.previewUrl

    var coverUrl by ThemeTable.coverUrl
    var isPublic by ThemeTable.isPublic
    var isFeatured by ThemeTable.isFeatured
    var downloadsSum by ThemeTable.downloadsSum

    var createdAt by ThemeTable.createdAt
    var latestUpdatedAt by ThemeTable.latestUpdatedAt

    var content by ContentEntity referencedOn ThemeTable.contentId
}
