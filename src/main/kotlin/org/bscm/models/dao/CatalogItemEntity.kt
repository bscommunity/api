package org.bscm.models.dao

import org.bscm.models.tables.CatalogItemTable
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class CatalogItemEntity(
    id: EntityID<String>
) : Entity<String>(id) {

    companion object :
        EntityClass<String, CatalogItemEntity>(CatalogItemTable)

    var type by CatalogItemTable.type
    var status by CatalogItemTable.status
    var visibility by CatalogItemTable.visibility

    var previewVideoId by CatalogItemTable.previewVideoId

    var isFeatured by CatalogItemTable.isFeatured

    var downloadsSum by CatalogItemTable.downloadsSum

    var discordChannelId by CatalogItemTable.discordChannelId
    var discordMessageId by CatalogItemTable.discordMessageId

    val versions by VersionEntity referrersOn
            VersionTable.catalogItemId

    var createdAt by CatalogItemTable.createdAt
    var publishedAt by CatalogItemTable.publishedAt
    var updatedAt by CatalogItemTable.updatedAt

    var author by UserEntity optionalReferencedOn CatalogItemTable.authorId

    /*
    val chart by ChartEntity optionalReferencedOn ChartTable
    val theme by ThemeEntity optionalReferencedOn ThemeTable
    val tourPass by TourPassEntity optionalReferencedOn TourPassTable
    */
    val chart: ChartEntity?
        get() = ChartEntity.findById(id)
    val theme: ThemeEntity?
        get() = ThemeEntity.findById(id)
    val tourPass: TourPassEntity?
        get() = TourPassEntity.findById(id)
}
